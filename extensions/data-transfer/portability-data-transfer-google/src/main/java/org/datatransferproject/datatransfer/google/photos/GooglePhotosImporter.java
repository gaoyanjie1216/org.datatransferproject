/*
 * Copyright 2018 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.datatransferproject.datatransfer.google.photos;

import static java.lang.String.format;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.JsonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import com.google.gdata.util.common.base.Pair;
import com.google.rpc.Code;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.datatransfer.google.common.GoogleCredentialFactory;
import org.datatransferproject.datatransfer.google.mediaModels.BatchMediaItemResponse;
import org.datatransferproject.datatransfer.google.mediaModels.GoogleAlbum;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItem;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItemResult;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItemUpload;
import org.datatransferproject.datatransfer.google.mediaModels.SimpleMediaItem;
import org.datatransferproject.datatransfer.google.mediaModels.Status;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.cloud.storage.TemporaryPerJobDataStore.InputStreamWrapper;
import org.datatransferproject.spi.cloud.types.PortabilityJob;
import org.datatransferproject.spi.transfer.i18n.BaseMultilingualDictionary;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.spi.transfer.types.PermissionDeniedException;
import org.datatransferproject.transfer.ImageStreamProvider;
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.common.models.photos.PhotosContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

/**
 * Google导入照片数据的容器
 */
public class GooglePhotosImporter implements Importer<TokensAndUrlAuthData, PhotosContainerResource> {

  private final GoogleCredentialFactory credentialFactory;
  private final JobStore jobStore;
  private final JsonFactory jsonFactory;
  private final ImageStreamProvider imageStreamProvider;
  private final Monitor monitor;
  private final double writesPerSecond;
  private final Map<UUID, GooglePhotosInterface> photosInterfacesMap;
  private final GooglePhotosInterface photosInterface;
  private final HashMap<UUID, BaseMultilingualDictionary> multilingualStrings = new HashMap<>();

  public GooglePhotosImporter(
          GoogleCredentialFactory credentialFactory,
          JobStore jobStore,
          JsonFactory jsonFactory,
          Monitor monitor,
          double writesPerSecond) {
    this(
            credentialFactory,
            jobStore,
            jsonFactory,
            new HashMap<>(),
            null,
            new ImageStreamProvider(),
            monitor,
            writesPerSecond);
  }

  @VisibleForTesting
  GooglePhotosImporter(
          GoogleCredentialFactory credentialFactory,
          JobStore jobStore,
          JsonFactory jsonFactory,
          Map<UUID, GooglePhotosInterface> photosInterfacesMap,
          GooglePhotosInterface photosInterface,
          ImageStreamProvider imageStreamProvider,
          Monitor monitor,
          double writesPerSecond) {
    this.credentialFactory = credentialFactory;
    this.jobStore = jobStore;
    this.jsonFactory = jsonFactory;
    this.photosInterfacesMap = photosInterfacesMap;
    this.photosInterface = photosInterface;
    this.imageStreamProvider = imageStreamProvider;
    this.monitor = monitor;
    this.writesPerSecond = writesPerSecond;
  }

  /**
   * 谷歌导入相册和图片
   * @param jobId the ID for the job
   * @param idempotentImportExecutor
   * @param authData authentication information
   * @param data the data
   * @return
   * @throws Exception
   */
  @Override
  public ImportResult importItem(
          UUID jobId,
          IdempotentImportExecutor idempotentImportExecutor,
          TokensAndUrlAuthData authData,
          PhotosContainerResource data) throws Exception {
    if (data == null) {
      // Nothing to do
      return ImportResult.OK;
    }

    // Uploads album metadata
    if (data.getAlbums() != null && data.getAlbums().size() > 0) {
      for (PhotoAlbum album : data.getAlbums()) {
        idempotentImportExecutor.executeAndSwallowIOExceptions(
                album.getId(), album.getName(), () -> importSingleAlbum(jobId, authData, album));
      }
    }

    long bytes = importPhotos(data.getPhotos(), idempotentImportExecutor, jobId, authData);

    final ImportResult result = ImportResult.OK;
    return result.copyWithBytes(bytes);
  }

  /**
   * 只是导入相册
   * @param jobId
   * @param authData
   * @param inputAlbum
   * @return
   */
  @VisibleForTesting
  String importSingleAlbum(UUID jobId, TokensAndUrlAuthData authData, PhotoAlbum inputAlbum)
          throws IOException, InvalidTokenException, PermissionDeniedException {
    // Set up album
    GoogleAlbum googleAlbum = new GoogleAlbum();
    String title = Strings.nullToEmpty(inputAlbum.getName());

    // Album titles are restricted to 500 characters
    // https://developers.google.com/photos/library/guides/manage-albums#creating-new-album
    if (title.length() > 500) {
      title = title.substring(0, 497) + "...";
    }
    googleAlbum.setTitle(title);
    googleAlbum.setId(inputAlbum.getId());

    // getOrCreatePhotosInterface 获取或创建谷歌图片类
    // createAlbum 执行post请求
    GooglePhotosInterface orCreatePhotosInterface = getOrCreatePhotosInterface(jobId, authData);
    GoogleAlbum responseAlbum = orCreatePhotosInterface.createAlbum(googleAlbum);
    return responseAlbum.getId();
  }

  /**
   * 导入图片
   * @param photos
   * @param executor
   * @param jobId
   * @param authData
   * @return
   * @throws Exception
   */
  long importPhotos(
          Collection<PhotoModel> photos,
          IdempotentImportExecutor executor,
          UUID jobId,
          TokensAndUrlAuthData authData) throws Exception {
    long bytes = 0L;
    // Uploads photos
    if (photos != null && photos.size() > 0) {
      Map<String, List<PhotoModel>> photosByAlbum =
              photos.stream()
                      .filter(photo -> !executor.isKeyCached(getIdempotentId(photo)))
                      .collect(Collectors.groupingBy(PhotoModel::getAlbumId));

      for (Entry<String, List<PhotoModel>> albumEntry : photosByAlbum.entrySet()) {
        String originalAlbumId = albumEntry.getKey();
        String googleAlbumId;
        if (Strings.isNullOrEmpty(originalAlbumId)) {
          // This is ok, since NewMediaItemUpload will ignore all null values and it's possible to
          // upload a NewMediaItem without a corresponding album id.
          // 这是ok的，因为NewMediaItemUpload将忽略所有的空值，这是可能的
          // 上传一个没有相应相册id的NewMediaItem
          googleAlbumId = null;
        } else {
          // Note this will throw if creating the album failed, which is what we want
          // because that will also mark this photo as being failed.
          //注意，如果创建相册失败，这是我们想要的，因为这也会标记这张照片为失败
          googleAlbumId = executor.getCachedValue(originalAlbumId);
        }

        // We partition into groups of 49 as 50 is the maximum number of items that can be created
        // in one call. (We use 49 to avoid potential off by one errors)
        //我们划分为49组，因为50是可以创建的最大条目数一次调用。(我们使用49来避免因一个错误而导致电位下降)
        // https://developers.google.com/photos/library/guides/upload-media#creating-media-item
        UnmodifiableIterator<List<PhotoModel>> batches =
                Iterators.partition(albumEntry.getValue().iterator(), 49);
        while (batches.hasNext()) {
          // 批量导入数据
          long batchBytes = importPhotoBatch(jobId, authData, batches.next(), executor, googleAlbumId);
          bytes += batchBytes;
        }
      }
    }
    return bytes;
  }

  /**
   * 批量导入照片
   * @param jobId 迁移任务id
   * @param authData 身份权限认证
   * @param photos
   * @param executor 执行器
   * @param albumId 对应相册id
   * @return
   * @throws Exception
   */
  long importPhotoBatch(
          UUID jobId,
          TokensAndUrlAuthData authData,
          List<PhotoModel> photos,
          IdempotentImportExecutor executor,
          String albumId) throws Exception {
    final ArrayList<NewMediaItem> mediaItems = new ArrayList<>();
    final HashMap<String, PhotoModel> uploadTokenToDataId = new HashMap<>();
    final HashMap<String, Long> uploadTokenToLength = new HashMap<>();

    // TODO: resumable uploads https://developers.google.com/photos/library/guides/resumable-uploads
    //  Resumable uploads would allow the upload of larger media that don't fit in memory.  To do
    //  this however, seems to require knowledge of the total file size.
    // 断点续传https://developers.google.com/photos/library/guides/resumable-uploads
    // 断点续传将允许上传不适合内存的更大的媒体。要做这似乎需要知道总文件大小
    SimpleMediaItem simpleMediaItem;
    NewMediaItem newMediaItem;
    for (PhotoModel photo : photos) {
      try {
        Pair<InputStream, Long> inputStreamBytesPair =
                // 根据url获取相应的InputStream流信息
                getInputStreamForUrl(jobId, photo.getFetchableUrl(), photo.isInTempStore());

        try (InputStream s = inputStreamBytesPair.getFirst()) {
          // TODO: 2022/2/16 获取谷歌图片类, 调用上传流post远程调用 uploadPhotoContent
          GooglePhotosInterface googlePhotosInterface = getOrCreatePhotosInterface(jobId, authData);
          // 上传图片流到云服务上
          String uploadToken = googlePhotosInterface.uploadPhotoContent(s);
          // final ArrayList<NewMediaItem> mediaItems = new ArrayList<>();
          // 上传的标题是图片的标题和描述部分
          simpleMediaItem = new SimpleMediaItem(uploadToken, photo.getTitle());
          newMediaItem = new NewMediaItem(cleanDescription(photo.getDescription()), simpleMediaItem);
          mediaItems.add(newMediaItem);
          uploadTokenToDataId.put(uploadToken, photo);
          uploadTokenToLength.put(uploadToken, inputStreamBytesPair.getSecond());
        }

        try {
          if (photo.isInTempStore()) {
            jobStore.removeData(jobId, photo.getFetchableUrl());
          }
        } catch (Exception e) {
          // Swallow the exception caused by Remove data so that existing flows continue
          monitor.info(() -> format(
                  "%s: Exception swallowed in removeData call for localPath %s",
                          jobId, photo.getFetchableUrl()), e);
        }
      } catch (IOException e) {
        executor.executeAndSwallowIOExceptions(
                // 获取相册id + dataId
                getIdempotentId(photo),
                photo.getTitle(),
                () -> {
                  System.out.println("error: " + e.getMessage());
                  throw e;
                });
      }
    }

    if (mediaItems.isEmpty()) {
      // Either we were not passed in any videos or we failed upload on all of them.
      System.out.println("mediaItems is Empty");
      return 0L;
    }

    long totalBytes = 0L;
    NewMediaItemUpload uploadItem = new NewMediaItemUpload(albumId, mediaItems);
    try {
      // 获取或创建谷歌图片类GooglePhotosInterface
      // 发送请求创建 createPhotos
      GooglePhotosInterface googlePhotosInterface = getOrCreatePhotosInterface(jobId, authData);

      // TODO: 2022/2/28 创建图片 请求url：https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate
      BatchMediaItemResponse photoCreationResponse = googlePhotosInterface.createPhotos(uploadItem);
      System.out.println("createPhotos result: " + photoCreationResponse);
      Preconditions.checkNotNull(photoCreationResponse);

      NewMediaItemResult[] mediaItemResults = photoCreationResponse.getResults();
      Preconditions.checkNotNull(mediaItemResults);
      for (NewMediaItemResult mediaItem : mediaItemResults) {
        PhotoModel photo = uploadTokenToDataId.get(mediaItem.getUploadToken());
        totalBytes +=
                processMediaResult(
                        mediaItem,
                        getIdempotentId(photo),
                        executor,
                        photo.getTitle(),
                        uploadTokenToLength.get(mediaItem.getUploadToken()));
        uploadTokenToDataId.remove(mediaItem.getUploadToken());
      }

      if (!uploadTokenToDataId.isEmpty()) {
        for (PhotoModel photo : uploadTokenToDataId.values()) {
          // 添加数据
          executor.executeAndSwallowIOExceptions(
                  getIdempotentId(photo),
                  photo.getTitle(),
                  () -> {
                    throw new IOException("Photo was missing from results list.");
                  });
        }
      }
    } catch (IOException e) {
      if (e.getMessage() != null
              && e.getMessage().contains("The remaining storage in the user's account is not enough")) {
        throw new DestinationMemoryFullException("Google destination storage full", e);
      } else {
        throw e;
      }
    }

    return totalBytes;
  }

  private long processMediaResult(
          NewMediaItemResult mediaItem,
          String idempotentId,
          IdempotentImportExecutor executor,
          String title,
          long bytes)
          throws Exception {
    Status status = mediaItem.getStatus();
    if (status.getCode() == Code.OK_VALUE) {
      executor.executeAndSwallowIOExceptions(
              idempotentId, title, () -> new PhotoResult(mediaItem.getMediaItem().getId(), bytes));
      return bytes;
    } else {
      executor.executeAndSwallowIOExceptions(
              idempotentId,
              title,
              () -> {
                throw new IOException(
                        String.format(
                                "Media item could not be created. Code: %d Message: %s",
                                status.getCode(), status.getMessage()));
              });
      return 0;
    }
  }

  /**
   * 根据url获取相应的InputStream流信息
   * @param jobId
   * @param fetchableUrl
   * @param inTempStore
   * @return
   */
  private Pair<InputStream, Long> getInputStreamForUrl(
          UUID jobId, String fetchableUrl, boolean inTempStore) throws IOException {
    if (inTempStore) {
      // 临时存储
      final InputStreamWrapper streamWrapper = jobStore.getStream(jobId, fetchableUrl);
      return Pair.of(streamWrapper.getStream(), streamWrapper.getBytes());
    }
    // TODO: 2022/2/16 根据url获取相应的InputStream流信息
     HttpURLConnection conn = imageStreamProvider.getConnection(fetchableUrl);
    return Pair.of(conn.getInputStream(), conn.getContentLengthLong() != -1 ? conn.getContentLengthLong() : 0);
  }

  String getIdempotentId(PhotoModel photo) {
    String albumIdAndDataId = photo.getAlbumId() + "-" + photo.getDataId();
    System.out.println("albumIdAndDataId: " + albumIdAndDataId);
    return albumIdAndDataId;
  }

  private String cleanDescription(String origDescription) {
    String description = Strings.isNullOrEmpty(origDescription) ? "" : origDescription;

    // Descriptions are restricted to 1000 characters
    // https://developers.google.com/photos/library/guides/upload-media#creating-media-item
    if (description.length() > 1000) {
      description = description.substring(0, 997) + "...";
    }
    return description;
  }

  /**
   * 获取或创建谷歌图片类
   * @param jobId
   * @param authData
   * @return
   */
  public synchronized GooglePhotosInterface getOrCreatePhotosInterface(
          UUID jobId, TokensAndUrlAuthData authData) {

    if (photosInterface != null) {
      return photosInterface;
    }

    if (photosInterfacesMap.containsKey(jobId)) {
      return photosInterfacesMap.get(jobId);
    }
    // 新建谷歌图片类，需要拿到获取认证的数据authData
    GooglePhotosInterface newInterface = makePhotosInterface(authData);
    photosInterfacesMap.put(jobId, newInterface);

    return newInterface;
  }

  /**
   * 创建谷歌PhotosInterface
   * @param authData
   * @return
   */
  private synchronized GooglePhotosInterface makePhotosInterface(TokensAndUrlAuthData authData) {
    Credential credential = credentialFactory.createCredential(authData);
    return new GooglePhotosInterface(
            credentialFactory, credential, jsonFactory, monitor, writesPerSecond);
  }

  private synchronized BaseMultilingualDictionary getOrCreateStringDictionary(UUID jobId) {
    if (!multilingualStrings.containsKey(jobId)) {
      PortabilityJob job = jobStore.findJob(jobId);
      String locale = job != null ? job.userLocale() : null;
      multilingualStrings.put(jobId, new BaseMultilingualDictionary(locale));
    }

    return multilingualStrings.get(jobId);
  }
}

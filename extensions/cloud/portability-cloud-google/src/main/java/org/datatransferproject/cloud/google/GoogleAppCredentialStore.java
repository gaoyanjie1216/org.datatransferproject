/*
 * Copyright 2018 The Data Transfer Project Authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.datatransferproject.cloud.google;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.cloud.google.GoogleCloudExtensionModule.ProjectId;
import org.datatransferproject.spi.cloud.storage.AppCredentialStore;
import org.datatransferproject.types.transfer.auth.AppCredentials;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

/**
 * App credential storage using Google Cloud Platform.
 *
 * <p>App keys are stored raw in Google Cloud Storage. App secrets are encrypted using Cloud KMS by
 * a project admin (using encrypt_and_upload_secrets.sh). This class returns {@link AppCredentials}
 * created by looking up keys and secrets in GCS, and decrypting the secrets with KMS.
 *
 * 应用凭证存储使用谷歌云平台。
 * 应用程序键原始存储在谷歌云存储。使用 Cloud KMS 加密应用程序的 secrets
 * 项目管理员(使用 encrypt_and_upload_secrets.sh)。这个类返回{@link AppCredentials}
 * 通过在GCS中查找密钥和秘密，并使用KMS解密这些秘密来创建。
 */
@Singleton
final class GoogleAppCredentialStore implements AppCredentialStore {
  private static final Integer CACHE_EXPIRATION_MINUTES = 10;
  private static final String APP_CREDENTIAL_BUCKET_PREFIX = "app-data-";
  private static final String KEYS_DIR = "keys/";
  private static final String KEY_EXTENSION = ".txt";
  private static final String SECRETS_DIR = "encrypted_secrets/";
  private static final String SECRET_EXTENSION = ".encrypted";

  private final GoogleAppSecretDecrypter appSecretDecrypter;
  private final Monitor monitor;
  private final Storage storage;
  private final String bucketName;

  /**
   * Store keys and secrets in a cache so we can reduce load on cloud storage / KMS when reading
   * keys and reading/decrypting secrets several times on startup.
   *
   * <p>Set the cache to reload keys/secrets periodically so that in the event of a key/secret being
   * compromised, we can update them without restarting our servers.
   *
   * 将keys and secrets 存储在缓存中，这样我们可以在读取时减少云存储/ KMS的负载
   * 密钥和读取/解密秘密启动几次。
   * 设置缓存周期性地重新加载密钥/秘密，以便在 keys/secrets 存在的情况下
   * 妥协后，我们可以在不重启服务器的情况下更新它们。
   */
  private final LoadingCache<String, String> keys;

  private final LoadingCache<String, String> secrets;

  @Inject
  GoogleAppCredentialStore(
      GoogleAppSecretDecrypter appSecretDecrypter,
      GoogleCredentials googleCredentials,
      @ProjectId String projectId,
      Monitor monitor) {
    this.appSecretDecrypter = appSecretDecrypter;
    this.monitor = monitor;
    this.storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(googleCredentials)
            .build()
            .getService();
    // Google Cloud Platform requires bucket names be unique across projects, so we include project
    // ID in the bucket name.
    // 谷歌云平台要求桶名在项目中是唯一的，所以我们包含了project 桶名中的ID
    this.bucketName = APP_CREDENTIAL_BUCKET_PREFIX + projectId;
    this.keys =
        CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
            .build(
                new CacheLoader<String, String>() {
                  @Override
                  public String load(String key) throws Exception {
                    return lookupKey(key);
                  }
                });
    this.secrets =
        CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
            .build(
                new CacheLoader<String, String>() {
                  @Override
                  public String load(String key) throws Exception {
                    return lookupSecret(key);
                  }
                });
  }

  @Override
  public AppCredentials getAppCredentials(String keyName, String secretName) throws IOException {
    String key;
    String secret;
    try {
      key = keys.get(keyName);
    } catch (ExecutionException e) {
      throw new IOException("Couldn't lookup key: " + keyName, e);
    }

    try {
      secret = secrets.get(secretName);
    } catch (ExecutionException e) {
      throw new IOException("Couldn't lookup secret: " + secretName, e);
    }

    return new AppCredentials(key, secret);
  }

  private byte[] getRawBytes(String blobName) {
    Bucket bucket = storage.get(bucketName);
    Preconditions.checkNotNull(bucket, "Bucket [%s] not found", bucketName);
    Blob blob = bucket.get(blobName);
    Preconditions.checkNotNull(blob, "blob [%s] not found", blobName);
    return blob.getContent();
  }

  private String lookupKey(String keyName) {
    String keyLocation = KEYS_DIR + keyName + KEY_EXTENSION;
    monitor.debug(
        () -> format("Getting app key for %s (blob %s) from bucket", keyName, keyLocation));
    byte[] rawKeyBytes = getRawBytes(keyLocation);
    checkState(rawKeyBytes != null, "Couldn't look up: " + keyName);
    return new String(rawKeyBytes).trim();
  }

  /**
   * 获取 secret
   * @param secretName
   * @return
   * @throws IOException
   */
  private String lookupSecret(String secretName) throws IOException {
    String secretLocation = SECRETS_DIR + secretName + SECRET_EXTENSION;
    monitor.debug(()->format("Getting app secret for %s (blob %s)", secretName, secretLocation));
    byte[] encryptedSecret = getRawBytes(secretLocation);
    checkState(encryptedSecret != null, "Couldn't look up: " + secretName);
    // 解密
    String secret = new String(appSecretDecrypter.decryptAppSecret(encryptedSecret)).trim();
    checkState(!Strings.isNullOrEmpty(secret), "Couldn't decrypt: " + secretName);
    return secret;
  }
}

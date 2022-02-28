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

import static java.nio.charset.StandardCharsets.UTF_8;
import org.datatransferproject.datatransfer.google.mediaModels.AlbumListResponse;
import org.datatransferproject.datatransfer.google.mediaModels.MediaItemSearchResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.rpc.Code;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.cloud.local.LocalJobStore;
import org.datatransferproject.datatransfer.google.common.GoogleCredentialFactory;
import org.datatransferproject.datatransfer.google.mediaModels.BatchMediaItemResponse;
import org.datatransferproject.datatransfer.google.mediaModels.GoogleAlbum;
import org.datatransferproject.datatransfer.google.mediaModels.GoogleMediaItem;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItemResult;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItemUpload;
import org.datatransferproject.datatransfer.google.mediaModels.Status;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.cloud.storage.TemporaryPerJobDataStore;
import org.datatransferproject.spi.cloud.types.PortabilityJob;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.idempotentexecutor.InMemoryIdempotentImportExecutor;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.spi.transfer.types.PermissionDeniedException;
import org.datatransferproject.transfer.ImageStreamProvider;
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.transfer.auth.AppCredentials;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;
import org.datatransferproject.types.transfer.errors.ErrorDetail;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class GooglePhotosImporterTest {

  private static final String OLD_ALBUM_ID = "OLD_ALBUM_ID";
  private static final String NEW_ALBUM_ID = "NEW_ALBUM_ID";
  private String PHOTO_TITLE = "Model photo title";
  private String PHOTO_DESCRIPTION = "Model photo description";
  private String IMG_URI = "image uri";
  private String JPEG_MEDIA_TYPE = "image/jpeg";
  private UUID uuid = UUID.randomUUID();
  private GooglePhotosImporter googlePhotosImporter;
  private GooglePhotosInterface googlePhotosInterface;
  private IdempotentImportExecutor executor;
  private ImageStreamProvider imageStreamProvider;
  private Monitor monitor;

  private String ACCESS_TOKEN
          = "ya29.A0ARrdaM_QnvasKXhDXurh3xMcoeDkjxq6Vwobnlu9rrCRTg5KFH3udRLj6msaQhcT1CqwRTd0kTTE5pxnkBggkj0UkQmWQZIxCTQvAS8OMqa2cdTOpHmpmx8KOE7GtnpREbTBSNZIFH77rVT_Ntf13y7z54Op";
  @Before
  public void setUp() throws IOException, InvalidTokenException, PermissionDeniedException {

    googlePhotosInterface = Mockito.mock(GooglePhotosInterface.class);
    monitor = Mockito.mock(Monitor.class);
    executor = new InMemoryIdempotentImportExecutor(monitor);

    Mockito.when(
            googlePhotosInterface.makePostRequest(
                anyString(), any(), any(), eq(NewMediaItemResult.class)))
        .thenReturn(Mockito.mock(NewMediaItemResult.class));

    JobStore jobStore = new LocalJobStore();

    InputStream inputStream = Mockito.mock(InputStream.class);
    imageStreamProvider = Mockito.mock(ImageStreamProvider.class);
    HttpURLConnection conn = Mockito.mock(HttpURLConnection.class);
    Mockito.when(imageStreamProvider.getConnection(anyString())).thenReturn(conn);
    Mockito.when(conn.getInputStream()).thenReturn(inputStream);
    Mockito.when(conn.getContentLengthLong()).thenReturn(32L);

    googlePhotosImporter =
        new GooglePhotosImporter(
            null, jobStore, null, null, googlePhotosInterface, imageStreamProvider, monitor, 1.0);
  }

  @Test
  public void importAlbum() throws Exception {
    // Set up
    String albumName = "888";
    String albumDescription = "Album description";
    PhotoAlbum albumModel = new PhotoAlbum("222", albumName, albumDescription);

    GoogleAlbum responseAlbum = new GoogleAlbum();
    responseAlbum.setId("111");
    // Run test
    GooglePhotosImporter googlePhotosImporter = getGooglePhotosImporter();
    //TokensAndUrlAuthData authData = generateAuthData();
    String accessToken = ACCESS_TOKEN;
    String refreshToken = "1//0eF-vVrAwFQPyCgYIARAAGA4SNwF-L9Ir9w3MoQEdAzq7bleB-yzdjajxZtgJb5AhnoT-B46Ki5V5QekTRn58HtHDfS1u6VP6Ax8";
    String url = "https://accounts.google.com/o/oauth2/token";
    TokensAndUrlAuthData authData = new TokensAndUrlAuthData(accessToken, refreshToken, url);
    String albumId = googlePhotosImporter.importSingleAlbum(uuid, authData, albumModel);
    System.out.println("albumId: " + albumId);

    // test333的相册id
    // AJ4dpAo28yC4gM26PdE04psigG8mSyohBQRL2Ee_BdnykfT0oAZu-jnJDy8unXH6YHSR93L-kvtF
    // GoogleAlbum{id='AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t', title='666'}
  }

  @Test
  public void getAlbums() throws Exception {

    // Run test
    GooglePhotosImporter googlePhotosImporter = getGooglePhotosImporter();
    //TokensAndUrlAuthData authData = generateAuthData();
    String accessToken = ACCESS_TOKEN;
    String refreshToken = "1//0eF-vVrAwFQPyCgYIARAAGA4SNwF-L9Ir9w3MoQEdAzq7bleB-yzdjajxZtgJb5AhnoT-B46Ki5V5QekTRn58HtHDfS1u6VP6Ax8";
    String url = "https://accounts.google.com/o/oauth2/token";
    TokensAndUrlAuthData authData = new TokensAndUrlAuthData(accessToken, refreshToken, url);

    GooglePhotosInterface googlePhotosInterface = googlePhotosImporter.getOrCreatePhotosInterface(uuid, authData);
    String pageToken = null;
    AlbumListResponse albumListResponse = googlePhotosInterface.listAlbums(Optional.ofNullable(pageToken));

    // [GoogleAlbum{id='AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t', title='666'},
    // GoogleAlbum{id='AJ4dpAo28yC4gM26PdE04psigG8mSyohBQRL2Ee_BdnykfT0oAZu-jnJDy8unXH6YHSR93L-kvtF', title='test333'},
    // GoogleAlbum{id='AJ4dpAq1Z5DsR3IktTdJiGKreVoGBWpGcBZy5QwmLc9yEK1Cm-7YUcGxrtwLpg43tXEkkHkmf-xk', title='test1111'},
    // GoogleAlbum{id='AJ4dpAqoItNnMQyKp6fxbiMv_QLZ02igqRzj2drJO33ods7jYqUcXQXgUPNwyqIDRR0FX8rF5_jN', title='Album Name'},
    // GoogleAlbum{id='AJ4dpAqlGSoesY3VSZWtnr5Y9CUsb0PtVPsXsawSzg94ysrB4A0wdzqkTXHElHQSNvPgir_irtkd', title='888'}], nextPageToken='null'}
    System.out.println("albumListResponse: " + albumListResponse);

    GoogleAlbum[] albums = albumListResponse.getAlbums();
    String albumId = albums[4].getId();
    MediaItemSearchResponse mediaItemSearchResponse = googlePhotosInterface.listMediaItems(Optional.of(albumId), Optional.empty());
    // mediaItemSearchResponse MediaItemSearchResponse{mediaItems=[GoogleMediaItem{id='AJ4dpAqwwwetyS4hHotRrxCNN5V-xcjT3-d-D7-3U6AZfpr0VZ3PjnNkYbPuh2pnR8ICY4tlojdnRgMZtsbGUxy15tzF-ikY3Q', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_bdqiNgJBT5xDWnHxsk7zA6wFVocLdR7ZMlrmmc9P6-HmRUvUKBzpWw3z6FLmc7guhzbQNsyusIht81-JYNYtarTN8mrfRFBr3rw_MLiLP1SJkJDCkiLgVNky6M6tfAwIggwfdFlGv_8qMZfbvqNoQLe94xcWkt96h3jDe4QTc3yE1V0U3G7e7oedyiaQ1g864SyY-byebyJb3_iNh3Y8leY7YDYkqCTk3jxeZp8WDwGEMdjtXG-e41bTpBQbmwoEMSYLjzxVIR_H9zjNSNfNek3WFXVfZ2wp38-9EaeeKdpDH0Lso_A7w3xf1V7mZuOyhpKDq9wtR_4br7N74jCSBcb03B176Mxy8D51U3KlOSNORdJ58aiaqi5KE3boQG8DLlqlIDRazf-IjDGqpki4sP28IuE2vtJT3o5904zsI89CYS_xnYF1elJ7ylr8Y8H4JFcLRKtkgxUZmtqi359H3OQoqoegp2l00t1L_gjxfTEt170HBZ-fzdJO2RQd0kRW3bx6RdE9PrshoV-Qoxv7eYwHUH3_egVdaPGD0JKVrCf9AqGpViw2N5SZ9RDlcRiAj1Z2SBtOb2gToKJRnpXIm4bqOsza_moV7HvmhAzk2oVLfyKjjh35DDuuSGP-p-Lc-SxxhNLVUQyjIb4BpKrydK09afMpuBdOKD107PetSZJnL4tamYFb0qV1SksbAWBRxZBl4LBLZzmiahAB2c-L5gKvSDSGZ2-NUpr2hNSjrzkKQoleCLQTtF-AYNlNb4Fw4U0chIp_ihNe0SYKRA62PN7MNG-IVglFf5VoT6OadpFrrw-37CjnO-13LXWZC9D3GvVnYAjJ0', mimeType='image/png', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@133bc66b, filename='2022-02-27.png', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpAqwwwetyS4hHotRrxCNN5V-xcjT3-d-D7-3U6AZfpr0VZ3PjnNkYbPuh2pnR8ICY4tlojdnRgMZtsbGUxy15tzF-ikY3Q'}, GoogleMediaItem{id='AJ4dpAqf-gv0C-xHI0ejLjauAzSZMuPPHAI-kn5cq_k513ZmPK4g541K3pu_td2veIWU8wVYj0zcWHGxXY4VA9OrzRGhbXyM3w', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_bLXRu5vAqY_76vJdCsck4qC7p6vXXIeQFmVu68AiMKIjJ8A5UUzpcVMJQMZSJp9FmrI8Pq4Ik0bWu98dPB2WyBZMu5OC09x9TuPHr_z6cKx4MkJEWXHOK5Lvn7_tKJYbBlmJi56L_4wIs2CLbvJ_WT82b-suSZLYf34LQfxZNFddZ1B1T6doCqQjjSidyUek7a-YpyKDC2w31vOc1MWB9aQwNKikMGUUDPkkVWgvO7kAm1yVzgVQj1vLKF_i8irklAYDFRcsiSVPQ97A2NPv52e8F2e-3aHlElP4565QLMM-V6CYtxw2HxsH7iLa0HGZLhiy19Bo4bjLhoa5f0PmxRVXuqnLDVeMqkAU__Z7swyZLZqEWUn3rlaXpEDKqMp8aPYo_nYxqjF7mT1oWhpAMVkAyxd_3n_lC_WulRtBW9EgkvTMeH4u15wmFJM75pO43rLM6ur8aL5duPyDUMaclsb6PVsIV8EHPOYWGqaZ1AKOcEzo8CboZ4pb2r2MxcVDheCuap04U9te5Vhqtou4HxKPzx5n65pYpgXbjsl5FmoMSR2eSU94QlKVkGo5pZj03eQ_TsX2ANHkOVstS_tCE304P2JScrC5IxyAII8VtWwNeTn4gGmoucTmyWgkmJxaOGx7s_lgOJyamDPRPa4oCecUShukrN2sFqBleTDtmHK432QyLK14kvDICd5TX7d35-_h1ZD1GmVQh9CP9k4QAfpfiiwTK-fRXNjtUE3qbasgdUgI1G7NoMsHX2FLlpIcA5xL2HFfyK4bDoroGMizqHeGgy7hPhZT6pjccz0o7Fp_dFTUxmrQ-4l2hdlBRCIT2ffb-plDY', mimeType='image/webp', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@ce41fdb, filename='2022-02-27.webp', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpAqf-gv0C-xHI0ejLjauAzSZMuPPHAI-kn5cq_k513ZmPK4g541K3pu_td2veIWU8wVYj0zcWHGxXY4VA9OrzRGhbXyM3w'}, GoogleMediaItem{id='AJ4dpArzUB9jqsqOewofu9grk1-Lh8bhWN4yVppoaueUdYEIQHm9ZuWWT6xyziHae7ggLmHjg0B15SqImRQ_aPaAWnleaQigng', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_btgmYAWIJGqyr0ZkhMcBjW5HdvtOlCD_dlnOq0kPzczm9rz5FH6KnOKUaH24j5AENEzLBAGzgvy-bXrHavennJg7crVPU8CzYuhNRx_ClpxhEEywopNIyCslnPuCEGL8uXaXFBD1TT92-L3Xh3TTAlDCa094kL5bXICUURapVy5oeX7weWNpXcGthUeVYrLkYxDg_aMEt-IjAsU_q6ZADCWbC0JFhxHqq0B4srLeaC46BUU1U36-Rzfsb9hUxj2zIJlruagIoEA38CJkchx4K1f5QOxcJ7DWK1bUqfGvvWPslM39LhaF7Q1moCmnsOhe6Fmr87Ec9VENubiFnDyliTthX83ZlsKCGRYPn9DCwytlFbaiSmuNJ3HuXz2z37Kb3LnWKIge5dqYILZ9Nfi04FwIQHmTmKi4iS9D_ZZO0c-WTQyrEfpBi3e3DG-S1NVmOt2RwSCckOvC60kYW-p2LqSEsjL6KHYN1ryeBI6dxwCFAr8HDEbXh8N6mgusYLrBcpNzqkufl0KQ5ZslBCx1p_tijKDL1Ig2TnFDqKVD3D46DwEYBxLc2K4fotzi8RdoGzbW_bWap5ZdCQ4PtODEJo1EFaXRK_6ow_9SzBx5C7KyxSGTATPtUukVSug_0C8wEAaXjuPGeUN_iVPc1pds6qy8ZPAAEz5Gow6OH9NhJsNC1Bwc9e4rHqP6GeeCaUKcRZ0Yxy3v0qdrCpft-nqT85iZakxmMS7m4Eu0bdKAyKYVeP66nLLG0f2FleorUOrQWCZdX3I73xExohSvhENkah-xwkv53jXNadsOLt58cTjD3qC4PiXqG3ze1-t1jXgQujtwExaVU', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@786895d1, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpArzUB9jqsqOewofu9grk1-Lh8bhWN4yVppoaueUdYEIQHm9ZuWWT6xyziHae7ggLmHjg0B15SqImRQ_aPaAWnleaQigng'}, GoogleMediaItem{id='AJ4dpArgdOaSQswJtq9OvtBXzMtn7s-d5BTttQgc5RMSvj6aeSa7r3baooPinzuYlO_KeYpvjZcjeOQ3HGkzKRm3k2dDsz3ZRw', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_Zs4-b6ke_eKcE4Aoob8-B_Ya058M17NsDPey5yZcbMEBY2-fXIyfg9DrHE5x9ahSBYpg2exkJ-3C-AOAZTlFJxq5oI-OkMKLPQEWoLxcIFeXLqrCrf_gO2v9Vy5BD7l5_FTdzOZI2fOpHFqtRvRkrtb9Blb-nLU8iGcuWq0XNKzYwNVb_5lEFdrSq_v53PEkQPeunKiFSxSjnvmm97OAimaFdBLrGIVy_UaylmcTnCUIE1AiR36TnnsnBXbhDo36YPOQgoanW9LnF8lZaOW9_HspnjWhpu8lDVSfz5LsLPRSwAOXngkCGL0MsyIdlxiWhGGOzIYgcC6_8X_XnUzx1un_ntnjSwHfoPZPoYMVMNq_hWXCUqlp0YnuaZ-U0fvmFsBoooFxSVEi-EZCt9LUpLsxraWrm6xXLheyssx5WTe6qsA-sbfbC7uBB-vx0FzSzAbN5YnaWuLhuNUSCALPgXZ2WE4pZHgM1pFYS7sPL_hGZEsRvpR0el8LvdXpHhn00TpwxMvbBxWN6RX6aB1QrHf0c_AhI83zoxoYfQgJzMjeTzzNx25gBExSg8Mx3C9ELreiuxgPmcZjq2WqfOLu3EDTMOOov9VQ2F8CiBzv4OFWhSTAUU7YP9Vg2_ag0KRB_JYihI5g4VtH5pA2cYqlGapQVrMHlnWW1KbRfpbwPDFAiI38bUTunK0J1dh_Y6gZSDQ-kVo-MVqL4NMAf4lpN_ERUwaRADO31hlHIlEyPDWquP1VNfJBHX8BzPzipDC6t34E8DGuSflEjNtdSydXvSYX64gf_dbDbLfUiL9AqHOcxU6_L_3hJwiEc4gluE791uFwpON2w', mimeType='image/png', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@36bfd8d8, filename='2022-02-27.png', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpArgdOaSQswJtq9OvtBXzMtn7s-d5BTttQgc5RMSvj6aeSa7r3baooPinzuYlO_KeYpvjZcjeOQ3HGkzKRm3k2dDsz3ZRw'}, GoogleMediaItem{id='AJ4dpAqxEU-fPz9y9gr7hc2K1i0p6JmUelei8cd9Gb07P0nXX18KnB6saOoy2AO1-VtVLrI3-8s__Mp85pN7CKT5AH3JCrqpEQ', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_aqdBbMug5FfOSTCaGY-Zw8lOnNq76CIpoAVED2lylBgzd6_BeWaJSId58TzvSQgfX0NjQawmH-D7ZuWjNrSzHPb2f5XIxW3rW14GQf0hUqYvJk2HAOoVK6q0xsIvqhYLTAdEtFg5nVJVVA0f3wbm0GT8ZQRoNFUfKrTLeaKxuUnohrIknK3mD5hSv0qsJorQW7F7GhrWD2bmw7Rk4elWWNduIqicjWebecKucHm-ghy4LIwb4ThUYqW5GsecH15k7FYn7yk6IC7G3pkkl-jcN2mZkGUwZLo-ojm0Dspcr9bkz41tV7jU5HeVS9u30WlN-_j7ojcwmjQC5aTeplHJlvmpaZQQMjWRHM8gJ1_KOiDq_R3QaMits48uupYMfERd8EweckjE4A9ZmnqcMX_KT4NvcgdGRNTIDmLJy0LoeAvnZABoY8W7pQou7FoxKVSJAdoXkfKjYrsgUC0DBr4faZaYvSNdqPVowckynaT28jSbP4RTYKTK6rY9qJAYJKWv_nTmD3gvb6ZCMevXJ_32VfWszYmFeeWM64AnThBzkyQES1l9cflCWpfYnkf3kqA-i6eIMoMuPV2LCuNWkFe7aIRZQdCTfHrznCj68m3lQLLqqEXWgvl6y3h5WWN0Nsg-csKXpxhZxDp08auEc8taKouMW3VlehzHPo89h2r-lzN-BrHOztN66CNECeEsfO0LlwKgbCSFtJjPwFNGNZ2eKaY7cgc4B5ocFsG-Xq4V1S4YqD-SNt2n0aSgcQ4Upb2f0PLF-852f6FfTw7QR_YU78J0gbcKqKHa_sMtzl9bnmhSZx1wlocyrLqPzxc30T0Ui7ElbrTZU', mimeType='image/png', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@7bfa48fa, filename='2022-02-27.png', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpAqxEU-fPz9y9gr7hc2K1i0p6JmUelei8cd9Gb07P0nXX18KnB6saOoy2AO1-VtVLrI3-8s__Mp85pN7CKT5AH3JCrqpEQ'}, GoogleMediaItem{id='AJ4dpAqIafaMZTHH-5sRHLdzqfDS1bU5uV-GiDekhAIiRS7s1DBeqAyS4MCbBuDunbi4OcVUVtGyQwSKf4u8XGciglJoSUDFTA', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_a6p2CKqCPImJMaSgqA-2oIVuMQqopUJDgo0wATAFqDaL7YkBBdfMw8znG9VQBdJDeY3O5bP5Z-MIaARXFdPYBdFZFT2WeRl5AjusGW-_uqcmN7iKsMTD7FSYn_uz4rv0oHBaA0kjfPJ97VUI05zf-hyQH84_xklEA-ZCqEUgl7rsRavaL00lZV6hDt5dtbnOEqAHvMBljS3BqUUOrGoLaRCywPeETGOOxS9TjH866kGBsIXttyRf_aQRhP2V0GKxAjPJ6Eq-Lk7Q53o7tjjQ9iXZIYIT-kK55jE28EUPzMHrRTVZDZzOgVf8oxyiXsUYlW6IOuTHAlwrN-32Vjs1Wxapa1od7J5iBR507cQiK2dORl8UN6yHi80UkBmN0EyHudCVjZA0VmOvpxoXlVvh2bLDoOx2x4wMUjzxpDpXrTOBEwg5rdYRIcKDikAn3X5UToHyl94B4nvuGo0eYTkISTk12AMv5gb9yM3ItPhVl37ODnSAMst981Kh4rus80QfLTEMlgrjgSvRacawD8hiY9HLJTTzupxl1OYPwWdgjlSDFZo0wr5w0E6bP2nXWOrOJj61mHTK-_wRHg9WtDVr8CjBFr305cqlm_iGnHdZsrLg7sXMfNmLEQPwbnsUBAk0Z2aeFZioJnG0YeHFQre-SMNAfWUftIJQCq_jQRPgW_Yim_VO8Wwu7sK-RRJrsM7ehoaFtlAeSsx6qfB3FK3YbQ8YUueG8zl_CF3i5lAojPPRjHu7XNDdfLDDHEGUjXZN5tC0hT87PU3XOSqPHqkPTxQw1J_45FNuM1aDk0DCPWKlQ5kUzUOdYWJRxCLQFi-mkRZsq93W0', mimeType='image/png', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@57cc9a99, filename='2022-02-27.png', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpAqIafaMZTHH-5sRHLdzqfDS1bU5uV-GiDekhAIiRS7s1DBeqAyS4MCbBuDunbi4OcVUVtGyQwSKf4u8XGciglJoSUDFTA'}, GoogleMediaItem{id='AJ4dpAr5Rzb7BuYcA6slxkrQcimQUUEjxWDjlWNz6r3KHzGVYSUX0EirOuEoYIO4eJOxASWVBmtV_RVNzxI4FW4v9rlQvGvSww', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_YAoeWycN2NAwYxe2ide8jsrY9LD2kxIZviGKBiMJziac7wmhpLbIyYXUz-d3vhj-sQGmsGotCFYozoUbBdRhwEbrU0tA7gINr-PHmNpaoYhq47gcO-1pnB5piNxd97QYJTrTKgO0FaSpYQSKPRKe8d0cZ0MXa977YM-217U08HzG_WvgBedd6Ihx5ke4iLLaiYANSxdJogYFVsuU2olJpwlFvWtb7oHZfzSKK15KvKSBm_cW988a_uEnx4d2jf4_67-uKsUe96GxLcrfYV8nS5UtX8w9iXH4cvwVulib1HRGtreC7J253F0imTWsrJUdumHlPksH2y-7nUN1UlbzA-c2L8LHc4OMjxAK4fYg9o_svXZg3Nej_ig2_AngxLeFC7Bsj7x4MB9HMrrXGFRvv8O2ec56UHJG54_cvPWv4aIMoRa5dpyPrtmW1Of0ngy5nBwvyT5vLZ-EQlg01rHoRVf3_siodp64PNBAPJQbNdT2vBtRVvefPreC8vOy9vv385v9R7WNIGRoEpEDjdw8mGJt1mMXug-3S2LmmRzvpmHk7Tp8YWYrXv76e6VzpaF2xXGrCgb72DHsPQmx_0rXbfccg-rbySWD5n2P9l6ntogC6CJ0xqfkd_x_tBz8MVpDmn1j4_CdgAHlLRLiewTPWj0s6DXktETnWakQ6uLI2RbNk5-COGZ_VjbJuiy_FXVKBdGD4-YqCYfFmvuiZaSC5BoL8KrSY27KRPKgPtbg1pu5acgGLg5-vsDidVBj0cY57aIGZrZYzNlD1-qAmbwonMCCL0C0t-jt25J13_Vhvq2YyDCpFXlkD1NN9UIBTSVVw_Iz5IKBY', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@6b364bac, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpAr5Rzb7BuYcA6slxkrQcimQUUEjxWDjlWNz6r3KHzGVYSUX0EirOuEoYIO4eJOxASWVBmtV_RVNzxI4FW4v9rlQvGvSww'}, GoogleMediaItem{id='AJ4dpApB0QvvY8OyUFcdgVnJO2ZiJRpQdHK8e_WDzMj8mtxCZ3U348YxxuT4DjrRC2Vf6Ps3tigWJIiVCyYYy5mfJmwzuF-QXg', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_b10lVKe8EZxVXZFbBSwlCky2bJT4qw8G1O-wkfdmyfs8OxBr2w2Kqr2Exu0ZxYMU1aY-Dg0EI-20VCWsm4YiOs6hekpX2HbWEcRuNokjxiy0_StnJ5woAKrQ0ZVTLRbFaLhS3S3ClY5c3InCPDDiMezNH8poao18zhVcKlg-0ep-lKE7SCEIWkE2nu-z8L85mH353A7NPFtillud6c_xZyv_qpk5zO1hBjECn4OWOYixLlX24_Si4_BFdFiRDXXRLM4DcBsriHej_9purcYp53FrXqPIpIHLZEMuQ-g_u1A03Eb0V6kk2CKVqvvWUEGyRv4jaT3vfPRIDQD9Cmi6ngxY1NQRrkBjB_IlYJuDIJYXbxoXwWmX7lOtEtya958G0VWAID6jOQOlkbR0VflveZ9FYCDiSJH7kiTpUBVFJhMt--_SH_FFg9KLSjgNtAXU_I5zO-vvmZ1oEZ7ySQBLHys_y7-PkRhQMPS5VdBzkmctBKRVY4qjv7b-mlINJPRu_aSjaJIDfR_uqOtwPmYYlS08fkfL8GhVz1l794Y1jQqdVv8pX7ROWFZ8wYd5xu8uOKAVmHm0i01-xYnuwYII6rROaPd7e5tMfOctVpuwX2qfT2lMrN3oZjVm-nhghFjTZK2KRowWX5ORVDKXU5fHhS-3rwKz8C_ECcgtqb6Dkn5IniGGdWKiOQ-8ZdnJCWGWXXIXuiJ6opOohkLFrHoMNUwY9BW8SxACMa4KfT1QFLffccwoRuejknXKPmytLn6zFccH7iYWbMNmcjmtR546oqrR4zrkQrOMBHnN-OlC42-YVMBcDFHJr2qXnyzpZUGdA3UBmKuTA', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@2387270a, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpApB0QvvY8OyUFcdgVnJO2ZiJRpQdHK8e_WDzMj8mtxCZ3U348YxxuT4DjrRC2Vf6Ps3tigWJIiVCyYYy5mfJmwzuF-QXg'}, GoogleMediaItem{id='AJ4dpAr0guhxgJTvC3EDNvqZL17MqgNNOtpsjHLDQNILQ5NbJzDwKzsSXg7oSQfbg4HBHX5VfuAGmO94Kp9fQZWxJtNWJYBRqw', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_bX0EGKmYFjeCMQrCI76k5xqF_R8qFY6bjLb3399-Oc8t2afNAJeXo47HWsDE0x2OVCtXZ64_AoImBeHUFERZkX94bYMBOpkpWu2xFzA87pn7fL6j3_dbaSCuuZoP6eWV46iZrgBmR3yr-qfOKRVCdTQRBoIErMQCLlJy-72P6N0M7B-Ba8NJvZj2xiZxYwiLACzmYAjDXfGcN4Iasf7bVmJxxLJ_Z9OVPSCoMmavYSB1A2xLD2UX0mMRM0HczybtZZRW9p7eo87_0Fswj_FyVDeijgHYK6FW7sGXFCfj4Nj1b7C00f3Vy_DSGyoEqIRnym_H8_8oab1fG936gNxlKFU3nTiPHypNz0ajoFS7Bq99cpdUx0J2zO7ggkeZ1qxIRx7-JLbrduywUb5wbh66l2E-spD10LCDY6aIgG1hMsqtGJjaY_8Yem3p96vmTfFu4qEEVmRYfv1nPFOvp8-VrmSXvuR9SENUPMqCMsRBXtkB4tkPZLKeW6yNn-pr1ooSO5G_JawR8mbvHh7MsPxt-KmCPxTdjfkNEknYua_5PN7VU-Cy7Q-bmh-N_aTndxaijMcCaj6TJzOt1XWd9nMOH_PMYSIbfCpyE-PVSm7hU-y2ncZt_6A2ykA7DSNUChBUNAME9piTSfSLc0a3gUC12EaDU7nX9UfXFoK0Q_qPYdiFTvF5gbixjIx26YHH1bhQAnXjBt0UylusI8amL-dCAzfm08LOSOeUXCoEsnp7r-0T3w_QuwNHwjnFn_gtU0zPM945pmHWVcbuQw_787lldK9YoBD_J-nk6eBnTRjFpSRhAZ8ClvmdNquiSPjrD-lYFg0a7H2QE', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@a931618, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpAr0guhxgJTvC3EDNvqZL17MqgNNOtpsjHLDQNILQ5NbJzDwKzsSXg7oSQfbg4HBHX5VfuAGmO94Kp9fQZWxJtNWJYBRqw'}, GoogleMediaItem{id='AJ4dpArZQrBw-zbfDBF3SvE9mo-loqIUvwy3DG_jYOKT5D_W5uVRk8gOBSrM9ALU8e6k90xpq_sxxC6EMqoqD2V5KjIQMyXU6Q', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_ZD8lZI8xT0H_yOCqGWs0Az6uIQeLw-rgcjlzRkij_dCssydMVeuDgbrx3qpgCfOKTdVTQRGfA5plrFxU9X_285lC-A_QJQzrOXdJQwxUrylRBskV_RW0S3Cmj9M9H99QkmRGsjS7F67Mm1HVvhzDkCU1Hnb5l3msXIKSA8vg7B3h080RNOAhQ9neA6nlFodC0VNy017rtnSHOSqR93BUu2aGJx3ez09ZHK0CsIhn-u-VdHJQt9sRJdKQa0MJ8P35sThvf87cLAjzQheUARXphy6jNsoqc4qvro2Yl4hOLJuYdtBUgZa5U-IbWWHSUgIMjuzWdRtDeXqfcCbYcShZIkNEuSLihRSprM6Jt8MmmOwxliRg00c5rhPA9-cOYBlql5Pez6V9aCyHX3_cgvRguNN76IHUvA0aPUqNgE9D6E0nhdy5kfoWtHzzCxdF3lnhXW-3nqFsBsDmESnpgHknyY_f67wVp95Ta9OwuIDWKt4p7P0l8YPRFiDrErcYKhbryLw40URj4IJQvsALargm4rj4qkzoXZIBOhRxNOoQKtsE40py1XsBwyq3hcI0Oz_o6OHlsGYNUAhCOAODVI6SMHsNFJ5IQwGMseiKLeNcGmRex0E_twPbTJ149ukF75aiHsYYs3-bL3SkAuKbv09Oa2E3RWNnyWXzDI4LVTsIVz8MgA2jk611TPrFOFfzocDJ-jFmHethJgyWtMqRzEMIOrUOUJAy_VTnt0GLXuK2TktIucOb73Y8ewS_MdD_m6ntLKkcIZRfFW0MdbDi7INi-iQU_Q3mo8qcbx90GaWzx3ayCotzZ5Ra1sNF1nhaHb5wydMQCWE3M', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@39b85d81, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpArZQrBw-zbfDBF3SvE9mo-loqIUvwy3DG_jYOKT5D_W5uVRk8gOBSrM9ALU8e6k90xpq_sxxC6EMqoqD2V5KjIQMyXU6Q'}, GoogleMediaItem{id='AJ4dpArcbVBif5eoKgxSdq8TKbCeo3VXqeY7T2pr2Q2_--B-bXVp1jGR8GGwY3-bgnv2SgNQQB2ss1qFwpMN2e9tjDsOSpEtRQ', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_Y4IytaoVpa7X_MM8ZnCCdkO7PfjRoTdyoCjCFJjl7LSXnQawqE2-SQsmZbhAM30Y-7KIjkk9B6dZjTdA_3_grzgb3NZE_r3au6jxsTyropsL_c-BbOvQ3_rp4dWLBkmcM4t2t8nJ9tOgQIEzab4wct3mQPYpTe70OlU7oqxHq2Za4AbZnn0-IuOQH-vucfmSBEUKS1cVwwJFGgwJVX3mO_e1ahgtK2VYnYUveKwWjRfvzsWTDn3nh7cxP4QH-JARKSX1vrW6ENkVoR1wPOH9RgK8orGVZ1JMoiUry4rhK5WixvzMfepf4qpK3rF-AQLvu7zjP_fQH3f4Kuuj0moRGrB0cc8vaIY4n76MSnKaQxRLnIrYkorv-fXgaRSaJac0gY9EigT2uhlI3abZIZ3S2EUVYZGXcVBvT67vfKarqCkMYrYZEkUf64E6ideaD1QyeWQX-m1ziivNqtAdy8dH9W7jGfXOphiztlY-QEo8_W0zvZ5bmlRlJJWBqUli0Vx0ebqytVS7ZqPRNAgrHNX4tk3FWoq4SXHxHh4duaAzMeETmzm9d7haHVtL9GPiizaex_moXbjHDr6dIj1ZK1FMvCA_A60Po2FZmgUwhR2u0h9PpG0JOGniL4tENt1qLSJC4OldQi8pXuFTbw7tWXCLKQCokgOSDwbjq5xOS5WPIN_UMU1o5jZPqjp7gYvxPAvlZNEdyHo7kedW7K3IXrkcyBpYwMoKUhayPqqV4Po9lf7AqdLZIw_wn648B-95xk-k5zPGStcvZR0gchLost052L6SIpZUPNVRMsCMPnQRkDs6xm88LgY6dpOaQlAB-mWppi0YffYD8', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@600a1a31, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpArcbVBif5eoKgxSdq8TKbCeo3VXqeY7T2pr2Q2_--B-bXVp1jGR8GGwY3-bgnv2SgNQQB2ss1qFwpMN2e9tjDsOSpEtRQ'}, GoogleMediaItem{id='AJ4dpArPIGrfueo3SxWHD_BhSmJ-hMGjPhf6sPxb-b4L0GW6pIq6oYoyXKHPdSSapZ5PBMnabK_vIn4IZapL1MYTWkzllV45Sw', description='Model photo description', baseUrl='https://lh3.googleusercontent.com/lr/AFBm1_aBO0dnOo9WhLPfiHntb9x0iJbBEiVPIzy3BpNuNxsNv5cTaQQUVTiMQYXVBF8NpCVlXRxYn7zuIrU4V5KLX6BOHW0sdh8sYJIwrom1oMYW80d8l4z679ZsdBOaenPCsh9HGORGH7HJFLGMqgsFyKOE-ey_XSHY2DOTG_Gf3s0_lTUglJe02Z8JB5qknfM3PB5TQ8oRSfHNWjPdcm9B2yUw4OoiPH8WM95g4Ez6IRQuow9As8EpDv01_GGXb8KaI0RwUefKYrv9tiDjNEZefdmWUuAhheGYPBqkR0EeIuKZc8bhXX-NTTXp0AxFJhIqf_zUIn3aj6DCZtQySe7GBDXicXRi3nc1avJxhCcfspG4GMEi3hItzVaSrWeY9EUhRT0lGKC6uxuZzV0e0eRMFtqQ6yA2gatPvPQIOQGpEUd1SgE260XZmR_z2ykMem9cScFP-EVsv3irJ8Oq4w4CvUeiloh9GoiTYcWQO-nJE7PUnfiPTRI1D073y7x0wy9IeFA0os07bx92bM-c6Trvd4vFsMOzgJf_nDFiA9BYqEd3MuC2zIhY_JbiBAXft9i9Xb0FuJ8yBO6DyZm0T5UOqrwOsJC9M7O09ssHRYxib9VC8vQe78Zz6F55uJXj1w1BeYTSqjParMkSW6_bFMSd5QcO3TQjBcA2vM5LRKFcWC1ktPftjlL0sHY1TbGHdtvd79bnBPamUmE9DkY3xPa77zjy5yKn-CKhJaaKbkFj5UI1WLbFAqvPee6Zsz9JCmiVcsa7RJ8ggDuSaH0SDY7oYd9zln2cDF9WiFBmw_wdnKudy8WbThy-hBeJmh3Cihz6FJExVk1e8Qw', mimeType='image/jpeg', mediaMetadata=org.datatransferproject.datatransfer.google.mediaModels.MediaMetadata@7a2fce4d, filename='2022-02-27.jpg', productUrl='https://photos.google.com/lr/album/AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t/photo/AJ4dpArPIGrfueo3SxWHD_BhSmJ-hMGjPhf6sPxb-b4L0GW6pIq6oYoyXKHPdSSapZ5PBMnabK_vIn4IZapL1MYTWkzllV45Sw'}], nextPageToken='null'}
    System.out.println("mediaItemSearchResponse " + mediaItemSearchResponse);
  }

  @Test
  public void getAlbumsAndPhotos() throws Exception {
    // Run test
    //TokensAndUrlAuthData authData = generateAuthData();
    String accessToken = ACCESS_TOKEN;
    String refreshToken = "1//0eF-vVrAwFQPyCgYIARAAGA4SNwF-L9Ir9w3MoQEdAzq7bleB-yzdjajxZtgJb5AhnoT-B46Ki5V5QekTRn58HtHDfS1u6VP6Ax8";
    String url = "https://accounts.google.com/o/oauth2/token";
    TokensAndUrlAuthData authData = new TokensAndUrlAuthData(accessToken, refreshToken, url);

    GooglePhotosImporter googlePhotosImporter = getGooglePhotosImporter();
    GooglePhotosInterface googlePhotosInterface = googlePhotosImporter.getOrCreatePhotosInterface(uuid, authData);

    String albumToken = null;
    AlbumListResponse albumListResponse;
    MediaItemSearchResponse containedMediaSearchResponse;
    List<MediaItemSearchResponse> list = new ArrayList<>();
    do {
      albumListResponse = googlePhotosInterface.listAlbums(Optional.ofNullable(albumToken));
      if (albumListResponse.getAlbums() != null) {
        for (GoogleAlbum album : albumListResponse.getAlbums()) {
          String albumId = album.getId();
          String photoToken = null;
          do {
            containedMediaSearchResponse =
                    // 根据某一个相册id获取相册下的
                    googlePhotosInterface.listMediaItems(Optional.of(albumId), Optional.ofNullable(photoToken));
            list.add(containedMediaSearchResponse);
            photoToken = containedMediaSearchResponse.getNextPageToken();
          } while (photoToken != null);
        }
      }
      albumToken = albumListResponse.getNextPageToken();
    } while (albumToken != null);

    System.out.println("list: " + list);
  }


  // 初始化
  private GooglePhotosImporter getGooglePhotosImporter() {
    AppCredentials appCredentials = new AppCredentials("GOOGLE_KEY", "GOOGLE_SECRET");

    JsonFactory jsonFactory = new JacksonFactory();
    HttpTransport httpTransport = new NetHttpTransport();
    GoogleCredentialFactory credentialFactory =
            new GoogleCredentialFactory(httpTransport, jsonFactory, appCredentials, monitor);
    JobStore jobStore = new LocalJobStore();

    GooglePhotosImporter googlePhotosImporter = new GooglePhotosImporter(
            credentialFactory,
            jobStore,
            jsonFactory,
            monitor, 1.0);
    return googlePhotosImporter;
  }

  public TokensAndUrlAuthData generateAuthData() {
    HttpTransport httpTransport = new NetHttpTransport();
    Map<String, String> params = new LinkedHashMap<>();
    String clientId = "757188424449-sleac6qmnlnm56v5fgnmclu6512gl0hc.apps.googleusercontent.com";
    String clientSecret = "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";
    String authCode = "4/0AX4XfWhJL-GXHA1_Xq4vk9oww__Md3--xbe7sCN49ItYky859PHDINlRe7tnAnQthZyvWw";
    String callbackBaseUrl = "https://localhost:8080";
    params.put("client_id", clientId);
    params.put("client_secret", clientSecret);
    params.put("grant_type", "authorization_code");
    params.put("redirect_uri", callbackBaseUrl);
    params.put("code", authCode);

    HttpContent content = new UrlEncodedContent(params);
    String tokenUrl = "https://www.googleapis.com/oauth2/v4/token";
    try {
      String tokenResponse = makeRawPostRequest(httpTransport, tokenUrl, content);
      TokensAndUrlAuthData responseClass = getResponseClass(tokenResponse);
      return responseClass;
    } catch (IOException e) {
      throw new RuntimeException("Error getting token", e);
    }
  }

  public TokensAndUrlAuthData getResponseClass(String result) throws IOException {
    OAuth2TokenResponse response = new ObjectMapper().readValue(result, OAuth2TokenResponse.class);
    return new TokensAndUrlAuthData(
            response.getAccessToken(),
            response.getRefreshToken(),
            "");
  }

  static String makeRawPostRequest(HttpTransport httpTransport, String url, HttpContent httpContent)
          throws IOException {
    HttpRequestFactory factory = httpTransport.createRequestFactory();
    HttpRequest postRequest = factory.buildPostRequest(new GenericUrl(url), httpContent);
    HttpResponse response = postRequest.execute();
    int statusCode = response.getStatusCode();
    if (statusCode != 200) {
      throw new IOException(
              "Bad status code: " + statusCode + " error: " + response.getStatusMessage());
    }
    return CharStreams.toString(new InputStreamReader(response.getContent(), UTF_8));
  }

  /**
   * 导入数据可能不会自动去重，相同的数据可能还会再次上传
   * @throws Exception
   */
  @Test
  public void importTwoPhotos() throws Exception {
    // 相册666的id
    String albumId = "AJ4dpApHhYqtnAg9DgwApe0PsBERXAB95bMH9fYzdkXnoxjO4pmnGR5n8EZkaMz9fboaiSwb134t";
    // 相册test333的id
    albumId = "AJ4dpAo28yC4gM26PdE04psigG8mSyohBQRL2Ee_BdnykfT0oAZu-jnJDy8unXH6YHSR93L-kvtF";

    // 888相册id
    albumId = "AJ4dpAqlGSoesY3VSZWtnr5Y9CUsb0PtVPsXsawSzg94ysrB4A0wdzqkTXHElHQSNvPgir_irtkd";

    // test111相册id
    albumId = "AJ4dpAq1Z5DsR3IktTdJiGKreVoGBWpGcBZy5QwmLc9yEK1Cm-7YUcGxrtwLpg43tXEkkHkmf-xk";

    String url1 = "https://eu.xmssdn.micloud.mi.com/2/1527852807128/get_thumbnail?sig=bGbZHW-Cj8z7JmBDZDe4kcLj4zA&data=eRt7jaQNQROaIbAwwPAzMO_YDvE03hHMKXKi0eZ8MY-fev0SDd6xW5EBrI65-KrG7wtT9C3v5J-PiTV8ufvRelzVAvS-sLCWPXnE2OcZzp_HayLtovewT_ZN034AZnCj7-VRQH42iYR3NP22hjeod2DA6pSLAFwT6KmYYVuqYUndi7pUJExct5n-DI50iSg9Z9pZ4seeI3MHBNMGje2kha3-XjzbMQuQCLnxrYpUG0wWQ5GbMobInlRnuWTWolXDdCNEy_LKw2AbdnGyQAtXU8Kkdkdv0OTLU5g8oSDyN6l2QzV-aHFwqsVT4Sk4mB_BGoOuUVtGr-vR3e6t12wzeg&ts=1646125385141&w=1080&h=1080&r=0&_cachekey=749625df2a74274537a22e376a484de3";
    String url2 = "http://10.38.162.140/2/1608633173353/get_thumbnail?sig=VLDBIv0piSaV4KnZRYIIcJQ1jlw&data=7a9__1Bhm0qj0dt7da32W5Vj8MtBnYypTVtROy1Zu4Dj6ea_j7uW1bGcpw7cqDlhmeYi8TNjHk0Fwl4aiIUIDjElYE5c6JXqCTvAe8_untnqk-KH_TRZEqGFLN849npH1nENIbKP7GUNSXASKyYsy74_lgq0NRfQqVibDlYxiBObNr9MX4EsqYVFFEwW6zbUbaP4mk0jsg3EoGQa3RcTj-9BAbsrHnE3FvbiVDBsaoKO4QXG_dI9tHMdCTvUAfKdhAIwya_sceK7T9vwT0lJ8fVPB4Gt6DqINI6Zi02HFHV4mIppudvlXQt-MTFTSGWA0jhPS97NRPCw&ts=1646619206672&w=1080&h=1080&r=0&_cachekey=b55215837e9dea27ae9fff7c000c6f1d";
    String url3 = "https://ali.xmssdn.micloud.mi.com/2/1566455553514/get_thumbnail?sig=zlEmx6zBSU8w_fj1IZIzmDyQiZo&data=Rz0-upesIl2e3JWIt4ZPfpVUHXJor5aYQS9w6tjdYbOO06EptrLQ6WO2vTha0umYIZzMaEtC6syuKrWoAq5wv0gKIDg_lBbS9x7TEC52kRTY7BsJn_nKRqDCggl7Wh0_GL6h8wMFRMrrCGJqsz5mwmEHp-ewFQxA7U7H0N-7cyK6hMVe8wINWQXgcqI40VzHO4IFZyExEbKui7ApnUsTaMngduXx6sI1VJFK9y96BD3fSAfqhI3vmRzf5_pkTBCHa0DdCOdM40Ye9s-YSsfb9stf7xgDbrVVryLrCtFnYH6wz4FcCQR7qkxLMlDxk2yCu3-5wkzmEcY&ts=1646107712139&w=1080&h=1080&r=0&_cachekey=a2a3a045f5bbf9a2adaa99f5aa7f9829";
    String url4 = "https://ali.xmssdn.micloud.mi.com/2/1566455553514/download_file?attachment=0&fn=eC5qcGc&ct=application%2Foctet-stream&meta=QzHg5R6p6Nl2-wQm6M33pt9w60wHY01ZMDWn709TYInjhZ8dvpxD5TYyEzY2HoLVnw7s3qKq-EJpMbWsFRVdXI2Z_xomdsi0plrNFNTbfWvE5PKbQdb0Eox2Eo14urpM56GWBq4-Pk3dvLCJoy07dPGm-d7_wROIoTKRE3LvKd7mg7XTBohYbqUhYb6w1EuozsbxaREWEbKJApJhjv1KRrGsaByKUMHvR4heasOf6TDu6atdr71lYibLDpQb3-SKi0rTqtU5eiAgHEr3n97WDPpe5yp-vLHKsFqpYbU84zCbQ51CieRUPGL85-30qZsGUvz0OB-erUSpSe25iz-dXG3N4cz8zHFtKxTGBYH4vpHIBsLcLPC8usLjOV0kx3QGKQ0T4fSWY-GzjuoWUGEu_CYNtINIrmKfygU&ts=1646022007000&sig=FxrD_R7rC9r0o7Px_eIiFwd4jwE&_cachekey=4896571904cc1d19392be592a05f9b6c";
    String url5 = "https://ali.xmssdn.micloud.mi.com/2/1566455553514/download_file?attachment=0&fn=eC5qcGc&ct=application%2Foctet-stream&meta=QzFqneoDjwcSW9oJubhkvBHlrozHj00_P4C78jIFJJP0-5opsvxU6ALJxJznlNnmMdDKeSyQ5KMfLr1rrkfzk0MpQ35W4snvzleoHTX13vPLs5CHMcLwAZFkS4II74YnRt--dix9LjseFwyIIfiaSDpQziY1rgzWyx8kVvcdvyZfto01N4CMLBCdLkVF_T4Ih75UeggGPumRgMJL7yVLIkBFc5GC6IG4Tfknjlh8gouGjcUu&ts=1646626241000&sig=cj0gl2auvGhe3PpjLfOxkD0BewM&_cachekey=7877a58a0641fabd53826c9b82a60b1a";

    PhotoModel photoModel1 =
            new PhotoModel(PHOTO_TITLE, url1, PHOTO_DESCRIPTION, JPEG_MEDIA_TYPE, "oldPhotoID1", albumId, false);
    PhotoModel photoModel2 =
            new PhotoModel(PHOTO_TITLE, url2, PHOTO_DESCRIPTION, JPEG_MEDIA_TYPE, "oldPhotoID2", albumId, false);
    PhotoModel photoModel3 =
            new PhotoModel(PHOTO_TITLE, url3, PHOTO_DESCRIPTION, JPEG_MEDIA_TYPE, "oldPhotoID3", albumId, false);
    PhotoModel photoModel4 =
            new PhotoModel(PHOTO_TITLE, url4, PHOTO_DESCRIPTION, JPEG_MEDIA_TYPE, "oldPhotoID4", albumId, false);
    PhotoModel photoModel5 =
            new PhotoModel("图片5标题", url5, "图片5介绍", "png", "oldPhotoID5", albumId, false);

    String accessToken = ACCESS_TOKEN;
    String refreshToken = "1//0eF-vVrAwFQPyCgYIARAAGA4SNwF-L9Ir9w3MoQEdAzq7bleB-yzdjajxZtgJb5AhnoT-B46Ki5V5QekTRn58HtHDfS1u6VP6Ax8";
    String url111 = "https://accounts.google.com/o/oauth2/token";
    TokensAndUrlAuthData authData = new TokensAndUrlAuthData(accessToken, refreshToken, url111);

    // 初始化设置组件参数
    GooglePhotosImporter googlePhotosImporter = getGooglePhotosImporter();

    // 批量导入图片返回图片的字节数
    long length =
        googlePhotosImporter.importPhotoBatch(
            UUID.randomUUID(), authData,
            Lists.newArrayList(photoModel1),
            executor, albumId);
    System.out.println("success length: " + length);
  }

  private NewMediaItemResult buildMediaItemResult(String uploadToken, int code) {
    // We do a lot of mocking as building the actual objects would require changing the constructors
    // which messed up deserialization so best to leave them unchanged.
    GoogleMediaItem mediaItem = Mockito.mock(GoogleMediaItem.class);
    Mockito.when(mediaItem.getId()).thenReturn("newId");
    Status status = Mockito.mock(Status.class);
    Mockito.when(status.getCode()).thenReturn(code);
    NewMediaItemResult result = Mockito.mock(NewMediaItemResult.class);
    Mockito.when(result.getUploadToken()).thenReturn(uploadToken);
    Mockito.when(result.getStatus()).thenReturn(status);
    Mockito.when(result.getMediaItem()).thenReturn(mediaItem);
    return result;
  }

  @Test
  public void importTwoPhotosWithFailure() throws Exception {
    PhotoModel photoModel1 =
        new PhotoModel(
            PHOTO_TITLE,
            IMG_URI,
            PHOTO_DESCRIPTION,
            JPEG_MEDIA_TYPE,
            "oldPhotoID1",
            OLD_ALBUM_ID,
            false);
    PhotoModel photoModel2 =
        new PhotoModel(
            PHOTO_TITLE,
            IMG_URI,
            PHOTO_DESCRIPTION,
            JPEG_MEDIA_TYPE,
            "oldPhotoID2",
            OLD_ALBUM_ID,
            false);

    // Mockito.when(googlePhotosInterface.uploadPhotoContent(any())).thenReturn("token1", "token2");
    BatchMediaItemResponse batchMediaItemResponse =
        new BatchMediaItemResponse(
            new NewMediaItemResult[] {
              buildMediaItemResult("token1", Code.OK_VALUE),
              buildMediaItemResult("token2", Code.UNAUTHENTICATED_VALUE)
            });
    Mockito.when(googlePhotosInterface.createPhotos(any(NewMediaItemUpload.class)))
        .thenReturn(batchMediaItemResponse);

    long length =
        googlePhotosImporter.importPhotoBatch(
            UUID.randomUUID(),
            Mockito.mock(TokensAndUrlAuthData.class),
            Lists.newArrayList(photoModel1, photoModel2),
            executor,
            NEW_ALBUM_ID);
    // Only one photo of 32L imported
    assertEquals(32L, length);
    assertTrue(executor.isKeyCached(googlePhotosImporter.getIdempotentId(photoModel1)));
    String failedDataId = googlePhotosImporter.getIdempotentId(photoModel2);
    assertFalse(executor.isKeyCached(failedDataId));
    ErrorDetail errorDetail = executor.getErrors().iterator().next();
    assertEquals(failedDataId, errorDetail.id());
    assertThat(errorDetail.exception(), CoreMatchers.containsString("Media item could not be created."));
  }

  @Test
  public void importAlbumWithITString()
      throws PermissionDeniedException, InvalidTokenException, IOException {
    String albumId = "Album Id";
    String albumName = "Album Name";
    String albumDescription = "Album Description";

    PhotoAlbum albumModel = new PhotoAlbum(albumId, albumName, albumDescription);

    PortabilityJob portabilityJob = Mockito.mock(PortabilityJob.class);
    Mockito.when(portabilityJob.userLocale()).thenReturn("it");
    JobStore jobStore = Mockito.mock(JobStore.class);
    Mockito.when(jobStore.findJob(uuid)).thenReturn(portabilityJob);
    GoogleAlbum responseAlbum = new GoogleAlbum();
    responseAlbum.setId(NEW_ALBUM_ID);
    Mockito.when(googlePhotosInterface.createAlbum(any(GoogleAlbum.class)))
        .thenReturn(responseAlbum);

    GooglePhotosImporter sut =
        new GooglePhotosImporter(
            null, jobStore, null, null, googlePhotosInterface, imageStreamProvider, monitor, 1.0);

    sut.importSingleAlbum(uuid, null, albumModel);
    ArgumentCaptor<GoogleAlbum> albumArgumentCaptor = ArgumentCaptor.forClass(GoogleAlbum.class);
    Mockito.verify(googlePhotosInterface).createAlbum(albumArgumentCaptor.capture());
    assertEquals(albumArgumentCaptor.getValue().getTitle(), albumName);
  }

  @Test
  public void retrieveAlbumStringOnlyOnce()
      throws PermissionDeniedException, InvalidTokenException, IOException {
    String albumId = "Album Id";
    String albumName = "Album Name";
    String albumDescription = "Album Description";

    PhotoAlbum albumModel = new PhotoAlbum(albumId, albumName, albumDescription);

    PortabilityJob portabilityJob = Mockito.mock(PortabilityJob.class);
    Mockito.when(portabilityJob.userLocale()).thenReturn("it");
    JobStore jobStore = Mockito.mock(JobStore.class);
    Mockito.when(jobStore.findJob(uuid)).thenReturn(portabilityJob);
    GoogleAlbum responseAlbum = new GoogleAlbum();
    responseAlbum.setId(NEW_ALBUM_ID);
    Mockito.when(googlePhotosInterface.createAlbum(any(GoogleAlbum.class)))
        .thenReturn(responseAlbum);

    GooglePhotosImporter sut =
        new GooglePhotosImporter(
            null, jobStore, null, null, googlePhotosInterface, imageStreamProvider, monitor, 1.0);

    sut.importSingleAlbum(uuid, null, albumModel);
    sut.importSingleAlbum(uuid, null, albumModel);
    Mockito.verify(jobStore, atMostOnce()).findJob(uuid);
  }

  @Test
  public void importPhotoInTempStore() throws Exception {
    PhotoModel photoModel =
        new PhotoModel(
            PHOTO_TITLE,
            IMG_URI,
            PHOTO_DESCRIPTION,
            JPEG_MEDIA_TYPE,
            "oldPhotoID1",
            OLD_ALBUM_ID,
            true);

    //Mockito.when(googlePhotosInterface.uploadPhotoContent(any())).thenReturn("token1");
    JobStore jobStore = Mockito.mock(LocalJobStore.class);
    Mockito.when(jobStore.getStream(any(), any()))
        .thenReturn(
            new TemporaryPerJobDataStore.InputStreamWrapper(
                new ByteArrayInputStream("TestingBytes".getBytes())));
    Mockito.doNothing().when(jobStore).removeData(any(), anyString());

    GooglePhotosImporter googlePhotosImporter =
        new GooglePhotosImporter(
            null, jobStore, null, null, googlePhotosInterface, null, null, 1.0);

    BatchMediaItemResponse batchMediaItemResponse =
        new BatchMediaItemResponse(
            new NewMediaItemResult[] {buildMediaItemResult("token1", Code.OK_VALUE)});

    Mockito.when(googlePhotosInterface.createPhotos(any(NewMediaItemUpload.class)))
        .thenReturn(batchMediaItemResponse);

    UUID jobId = UUID.randomUUID();

    long length =
        googlePhotosImporter.importPhotoBatch(
            jobId,
            Mockito.mock(TokensAndUrlAuthData.class),
            Lists.newArrayList(photoModel),
            executor,
            NEW_ALBUM_ID);
    assertTrue(executor.isKeyCached(googlePhotosImporter.getIdempotentId(photoModel)));
    Mockito.verify(jobStore, Mockito.times(1)).removeData(any(), anyString());
    Mockito.verify(jobStore, Mockito.times(1)).getStream(any(), anyString());
  }

  @Test
  public void importPhotoInTempStoreFailure() throws Exception {
    PhotoModel photoModel =
        new PhotoModel(
            PHOTO_TITLE,
            IMG_URI,
            PHOTO_DESCRIPTION,
            JPEG_MEDIA_TYPE,
            "oldPhotoID1",
            OLD_ALBUM_ID,
            true);

    /*Mockito.when(googlePhotosInterface.uploadPhotoContent(any()))
        .thenThrow(new IOException("Unit Testing"));*/
    JobStore jobStore = Mockito.mock(LocalJobStore.class);
    Mockito.when(jobStore.getStream(any(), any()))
        .thenReturn(
            new TemporaryPerJobDataStore.InputStreamWrapper(
                new ByteArrayInputStream("TestingBytes".getBytes())));
    Mockito.doNothing().when(jobStore).removeData(any(), anyString());

    GooglePhotosImporter googlePhotosImporter =
        new GooglePhotosImporter(
            null, jobStore, null, null, googlePhotosInterface, null, null, 1.0);

    BatchMediaItemResponse batchMediaItemResponse =
        new BatchMediaItemResponse(
            new NewMediaItemResult[] {buildMediaItemResult("token1", Code.OK_VALUE)});

    Mockito.when(googlePhotosInterface.createPhotos(any(NewMediaItemUpload.class)))
        .thenReturn(batchMediaItemResponse);

    UUID jobId = UUID.randomUUID();

    googlePhotosImporter.importPhotoBatch(
        jobId,
        Mockito.mock(TokensAndUrlAuthData.class),
        Lists.newArrayList(photoModel),
        executor,
        NEW_ALBUM_ID);
    Mockito.verify(jobStore, Mockito.times(0)).removeData(any(), anyString());
    Mockito.verify(jobStore, Mockito.times(1)).getStream(any(), anyString());
  }
}

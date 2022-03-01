package org.datatransferproject.types.transfer.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** Token-based authentication data. */
@JsonTypeName("org.dataportability:TokensAndUrlAuthData")
public class TokensAndUrlAuthData extends AuthData {
  /**
   * 访问token，默认有效期是1小时，到期需要刷新重新获取token
   */
  private final String accessToken;
  /**
   * 刷新token
   */
  private final String refreshToken;
  /**
   * 编码的授权服务器URL
   */
  private final String tokenServerEncodedUrl;

  @JsonCreator
  public TokensAndUrlAuthData(
      @JsonProperty("accessToken") String accessToken,
      @JsonProperty("refreshToken") String refreshToken,
      @JsonProperty("tokenServerEncodedUrl") String tokenServerEncodedUrl) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.tokenServerEncodedUrl = tokenServerEncodedUrl;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public String getTokenServerEncodedUrl() {
    return tokenServerEncodedUrl;
  }

  @JsonIgnore
  @Override
  public String getToken() {
    return getAccessToken();
  }
}

package org.datatransferproject.spi.api.auth;

import org.datatransferproject.spi.api.types.AuthFlowConfiguration;
import org.datatransferproject.types.transfer.auth.AuthData;

/**
 * Generates data for authentication flows.
 *
 * <p>REVIEW: removed IOException
 */
public interface AuthDataGenerator {

  /**
   * Provide a authUrl to redirect the user to authenticate. In the Oauth2 case, this is the
   * authorization code authUrl.
   *
   * @param callbackBaseUrl the url to the api server serving the callback for the auth request
   * @param id is a client supplied identifier
   *
   * 提供一个 authUrl 来重定向用户进行身份验证。在Oauth2的例子中，这是 authUrl 授权代码。
   * @param callbackBaseUrl 指向api服务器的url，该url为认证请求提供回调服务
   * @param id 是客户端提供的标识符
   */
  AuthFlowConfiguration generateConfiguration(String callbackBaseUrl, String id);

  /**
   * Generate auth data given the code, identifier, and, optional, initial auth data that was used
   * for earlier steps of the authentication flow.
   *
   * @param callbackBaseUrl the url to the api server serving the callback for the auth request
   * @param authCode The authorization code or oauth verififer after user authorization
   * @param id is a client supplied identifier
   * @param initialAuthData optional data resulting from the initial auth step
   * @param extra optional additional code, password, etc.
   *
   * 给定代码、标识符和所使用的可选初始身份验证数据，生成身份验证数据身份验证流的前面步骤
   * @param callbackBaseUrl 指向api服务器的url，该url为认证请求提供回调服务
   * @param authCode 用户授权后的授权码或oauth验证码
   * @param id 是客户端提供的标识符
   * @param initialAuthData 初始认证步骤产生的可选数据
   * @param extra 可选的额外代码、密码等
   */
  AuthData generateAuthData(
      String callbackBaseUrl, String authCode, String id, AuthData initialAuthData, String extra);

  /**
   * Return the URL used to exchange an access code for token.
   *
   * <p>TODO(#553): Implement this for auth extensions. Currently unused by the demo-server frontend.
   */
  default String getTokenUrl() {
    return "";
  }
}

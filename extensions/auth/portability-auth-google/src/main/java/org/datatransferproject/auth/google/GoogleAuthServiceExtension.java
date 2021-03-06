/*
 * Copyright 2018 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.datatransferproject.auth.google;

import org.datatransferproject.auth.OAuth2ServiceExtension;
import org.datatransferproject.spi.api.auth.extension.AuthServiceExtension;

/**
 * An {@link AuthServiceExtension} providing an authentication mechanism for Google services.
 *
 * 一个为谷歌服务提供认证机制的{@link AuthServiceExtension}。
 */
public class GoogleAuthServiceExtension extends OAuth2ServiceExtension {

  public GoogleAuthServiceExtension() {
    super(new GoogleOAuthConfig());
  }

}

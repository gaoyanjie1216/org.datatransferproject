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
 * Create a new temp file (e.g. Test.java) in Intellij.
 * Inside the file, enter Alt-Insert -> Copyright
 * It should prompt you to select the new Copyright profile
 * Test out another new test file - the copyright should be imported automatically (note: it might be collapsed so not immediately obvious)
 */
package org.datatransferproject.spi.service.extension;

import org.datatransferproject.api.launcher.AbstractExtension;

/**
 * An extension that provides core runtime services used by other extensions.
 *
 * <p>Service extensions are guaranteed to be loaded prior to other extension types.
 *
 * 提供其他扩展使用的核心运行时服务的扩展。
 * 服务扩展保证在其他扩展类型之前加载。
 */
public interface ServiceExtension extends AbstractExtension {}

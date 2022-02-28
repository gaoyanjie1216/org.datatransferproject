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
package org.datatransferproject.api.launcher;

/** Implementations provide services required to boot a runtime system.
 * 实现提供了启动运行时系统所需的服务
 */
public interface BootExtension extends SystemExtension {

  /** Initializes the extension. Implementations prepare core bootstrap services.
   * 初始化扩展。实现准备核心引导服务。
   */
  void initialize();
}

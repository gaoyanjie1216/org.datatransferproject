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
package org.datatransferproject.spi.transfer.extension;

import org.datatransferproject.api.launcher.AbstractExtension;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.spi.transfer.provider.Importer;

/** Transfer extensions implement this contract to be loaded in a transfer worker process.
 * 传输扩展实现此合同以加载到传输工作进程中
 * */
public interface TransferExtension extends AbstractExtension {

  /** The key associated with this extension's service.
   * 与此扩展服务关联的密钥 */
  String getServiceId();

  /** Returns initialized extension exporter. */
  Exporter<?, ?> getExporter(String transferDataType);

  /** Returns initialized extension importer. */
  Importer<?, ?> getImporter(String transferDataType);
}

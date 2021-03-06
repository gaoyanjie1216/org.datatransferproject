/*
 * Copyright 2019 The Data Transfer Project Authors.
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

import java.time.Duration;

/**
 * Interface to log metrics about a DTP job.
 *
 * <p>Note this class is to be used by DTP framework code only, transfer extensions should use:
 * {@link MetricRecorder} which pipes calls into the recordGeneric* methods.
 *
 * <p>Cloud extensions should implement this interface to records stats to their preferred stats
 * platform.
 *
 * 该接口用于记录关于DTP作业的度量。
 * 注意这个类只能被DTP框架代码使用，传输扩展应该使用:
 * {@link MetricRecorder}管道调用到recordGeneric*方法。
 * 云扩展应该实现这个接口记录统计到他们的首选统计平台。
 */
public interface DtpInternalMetricRecorder {
  // Metrics related to DTP internals

  /** A DTP job started. **/
  void startedJob(String dataType, String exportService, String importService);
  /** A DTP job finished **/
  void finishedJob(
      String dataType,
      String exportService,
      String importService,
      boolean success,
      Duration duration);
  /** A DTP job cancelled **/
  void cancelledJob(
      String dataType,
      String exportService,
      String importService,
      Duration duration);

  /** An single attempt to export a page of data finished. **/
  void exportPageAttemptFinished(
      String dataType,
      String service,
      boolean success,
      Duration duration);

  /** An attempt to export a page of data finished including all retires. **/
  void exportPageFinished(String dataType, String service, boolean success, Duration duration);

  /** An single attempt to import a page of data finished. **/
  void importPageAttemptFinished(
      String dataType,
      String service,
      boolean success,
      Duration duration);

  /** An attempt to import a page of data finished including all retires. **/
  void importPageFinished(String dataType, String service, boolean success, Duration duration);

  // Metrics from {@link MetricRecorder}
  void recordGenericMetric(String dataType, String service, String tag);
  void recordGenericMetric(String dataType, String service, String tag, boolean bool);
  void recordGenericMetric(String dataType, String service, String tag, Duration duration);
  void recordGenericMetric(String dataType, String service, String tag, int value);
}

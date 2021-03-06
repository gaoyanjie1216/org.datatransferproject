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
package org.datatransferproject.transfer;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import java.util.UUID;

/**
 * A class that contains metadata for a transfer worker's job.
 * <p>
 * <p>This class is completely static to ensure it is a singleton within each transfer worker
 * instance.
 * 包含传输worker的元数据的类。
 * 这个类是完全静态的，以确保它在每个传输worker中是一个单例实例。
 */
@SuppressWarnings("WeakerAccess")
// We make the class and various methods public so they can be accessed from Monitors
// 我们将类和各种方法变成公共的，这样就可以从监视器中访问它们
public final class JobMetadata {
  private static byte[] encodedPrivateKey = null;
  private static UUID jobId = null;
  private static String dataType = null;
  private static String exportService = null;
  private static String importService = null;
  private static Stopwatch stopWatch = null;

  public static boolean isInitialized() {
    return (jobId != null
        && encodedPrivateKey != null
        && dataType != null
        && exportService != null
        && importService != null
        && stopWatch != null);
  }

  static void init(
      UUID initJobId,
      byte[] initEncodedPrivateKey,
      String initDataType,
      String initExportService,
      String initImportService,
      Stopwatch initStopWatch) {
    Preconditions.checkState(!isInitialized(), "JobMetadata cannot be initialized twice");
    jobId = initJobId;
    encodedPrivateKey = initEncodedPrivateKey;
    dataType = initDataType;
    exportService = initExportService;
    importService = initImportService;
    stopWatch = initStopWatch;
  }

  // TODO: remove this
  static synchronized void reset() {
    jobId = null;
    encodedPrivateKey = null;
    dataType = null;
    exportService = null;
    importService = null;
    stopWatch = null;
  }

  static byte[] getPrivateKey() {
    Preconditions.checkState(isInitialized(), "JobMetadata must be initialized");
    return encodedPrivateKey;
  }

  public static UUID getJobId() {
    Preconditions.checkState(isInitialized(), "JobMetadata must be initialized");
    return jobId;
  }

  public static String getDataType() {
    Preconditions.checkState(isInitialized(), "JobMetadata must be initialized");
    return dataType;
  }

  public static String getExportService() {
    Preconditions.checkState(isInitialized(), "JobMetadata must be initialized");
    return exportService;
  }

  public static String getImportService() {
    Preconditions.checkState(isInitialized(), "JobMetadata must be initialized");
    return importService;
  }

  public static Stopwatch getStopWatch() {
    Preconditions.checkState(isInitialized(), "JobMetadata must be initialized");
    return stopWatch;
  }
}

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

import com.google.inject.Inject;

/**
 * Polls a {@code PortabilityJob} for this transfer worker to process.
 *
 * <p>
 *
 * <p>Lightweight wrapper around an {@code AbstractScheduledService} so as to not expose its
 * implementation details.
 *
 * 轮询一个{@code PortabilityJob}，以便此转移工作 进行处理。
 * 围绕{@code AbstractScheduledService}的轻量级包装器，这样就不会暴露它的实现细节
 */
final class JobPoller {
  private final JobPollingService jobPollingService;

  @Inject
  JobPoller(JobPollingService jobPollingService) {
    this.jobPollingService = jobPollingService;
  }

  void pollJob() {
    jobPollingService.startAsync();
    jobPollingService.awaitTerminated();
  }
}

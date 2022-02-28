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

package org.datatransferproject.spi.transfer.idempotentexecutor;

import java.util.UUID;
import org.datatransferproject.types.transfer.errors.ErrorDetail;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * A utility that will execute a {@link Callable} only once for a given {@code idempotentId}. This
 * allows client code to be called multiple times in the case of retries without worrying about
 * duplicating imported data.
 */
public interface IdempotentImportExecutor {
  /**
   * Executes a callable, a callable will only be executed once for a given idempotentId, subsequent
   * calls will return the same result as the first invocation if it was successful.
   *
   * <p>If the provided callable throws an IO exception if is logged and ignored and null is
   * returned. All other exceptions are passed through
   *
   * <p>This is useful for leaf level imports where the importer should continue if a single item
   * can't be imported.
   *
   * <p>Any errors (that aren't latter successful) will be reported as failed items.
   *
   * @param idempotentId a unique ID to prevent data from being duplicated
   * @param itemName a user visible/understandable string to be displayed to the user if the item
   *     can't be imported
   * @param callable the callable to execute
   * @return the result of executing the callable.
   *
   * 执行一个可调用对象，对于给定的idempotentId，该可调用对象将只执行一次，随后执行
   * 如果成功，调用将返回与第一次调用相同的结果。
   * 如果提供的可调用对象被记录并被忽略且为空，则抛出IO异常返回。所有其他例外都被通过
   * 这对于叶子级导入非常有用，在叶子级导入中，如果只有一个项目，导入器应该继续执行不能导入。
   * 任何错误(后面不成功的)都将报告为失败的条目。
   * @param idempotentId 一个唯一的ID，以防止数据被复制
   * @param itemName 一个用户可见/可理解的字符串，如果项目显示给用户不能导入
   * @param callable 可调用对象
   * @return 返回调用对象的执行结果。
   */
  @Nullable
  <T extends Serializable> T executeAndSwallowIOExceptions(
      String idempotentId, String itemName, Callable<T> callable) throws Exception;

  /**
   * Executes a callable, a callable will only be executed once for a given idempotentId, subsequent
   * calls will return the same result as the first invocation if it was successful.
   *
   * <p>If the provided callable throws an exception then that is exception is rethrown.
   *
   * <p>This is useful for container level items where the rest of the import can't continue if
   * there is an error.
   *
   * <p>Any errors (that aren't latter successful) will be reported as failed items.
   *
   * @param idempotentId a unique ID to prevent data from being duplicated
   * @param itemName a user visible/understandable string to be displayed to the user if the item
   *     can't be imported
   * @param callable the callable to execute
   * @return the result of executing the callable.
   */
  <T extends Serializable> T executeOrThrowException(
      String idempotentId, String itemName, Callable<T> callable) throws Exception;

  /**
   * Returns a cached result from a previous call to {@code execute}.
   *
   * @param idempotentId a unique ID previously passed into {@code execute}
   * @return the result of a previously evaluated {@code execute} call
   * @throws IllegalArgumentException if the key is not found
   */
  <T extends Serializable> T getCachedValue(String idempotentId) throws IllegalArgumentException;

  /** Checks if a given key has been cached already. */
  boolean isKeyCached(String idempotentId);

  /** Get the set of all errors that occurred, and weren't subsequently successful. */
  Collection<ErrorDetail> getErrors();

  /**
   * Sets the jobId for the IdempotentImportExecutor sot that any values can be linked to the job.
   * This can enable resuming a job even in the situation that a transfer worker crashed without
   * creating duplicate items. Some IdempotentImportExecutors may require this to be called before
   * execution.
   * @param jobId The jobId of the job that this IdempotentImportExecutor is being used for
   */
  void setJobId(UUID jobId);


  /** Get the set of recent errors that occurred, and weren't subsequently successful. */
  default Collection<ErrorDetail> getRecentErrors() {
    return getErrors();
  }

  /** Reset recent errors to empty set */
  default void resetRecentErrors() {}

}

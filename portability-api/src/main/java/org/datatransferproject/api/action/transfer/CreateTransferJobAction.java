package org.datatransferproject.api.action.transfer;

import static java.lang.String.format;
import static org.datatransferproject.api.action.ActionUtils.encodeJobId;
import static org.datatransferproject.spi.api.auth.AuthServiceProviderRegistry.AuthMode.EXPORT;
import static org.datatransferproject.spi.api.auth.AuthServiceProviderRegistry.AuthMode.IMPORT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.datatransferproject.api.action.Action;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.api.launcher.TypeManager;
import org.datatransferproject.launcher.monitor.events.EventCode;
import org.datatransferproject.security.EncrypterFactory;
import org.datatransferproject.security.SymmetricKeyGenerator;
import org.datatransferproject.spi.api.auth.AuthDataGenerator;
import org.datatransferproject.spi.api.auth.AuthServiceProviderRegistry;
import org.datatransferproject.spi.api.types.AuthFlowConfiguration;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.cloud.types.JobAuthorization;
import org.datatransferproject.spi.cloud.types.PortabilityJob;
import org.datatransferproject.types.client.transfer.CreateTransferJob;
import org.datatransferproject.types.client.transfer.TransferJob;
import org.datatransferproject.types.common.ExportInformation;

/**
 * Creates a transfer job and prepares it for both the export and import service authentication
 * flow.
 * 创建一个传输作业，并为导出和导入服务身份验证做好准备流
 */
public class CreateTransferJobAction implements Action<CreateTransferJob, TransferJob> {

  private final JobStore jobStore;
  private final AuthServiceProviderRegistry registry;
  private final SymmetricKeyGenerator symmetricKeyGenerator;
  private final ObjectMapper objectMapper;
  private final EncrypterFactory encrypterFactory;
  private final Monitor monitor;

  @Inject
  CreateTransferJobAction(
      JobStore jobStore,
      AuthServiceProviderRegistry registry,
      SymmetricKeyGenerator symmetricKeyGenerator,
      TypeManager typeManager,
      Monitor monitor) {
    this.jobStore = jobStore;
    this.registry = registry;
    this.symmetricKeyGenerator = symmetricKeyGenerator;
    this.objectMapper = typeManager.getMapper();
    this.encrypterFactory = new EncrypterFactory(monitor);
    this.monitor = monitor;
  }

  @Override
  public Class<CreateTransferJob> getRequestType() {
    return CreateTransferJob.class;
  }

  /**
   * 处理生成传输任务的处理器
   * @param request
   * @return
   */
  @Override
  public TransferJob handle(CreateTransferJob request) {
    String dataType = request.getDataType();
    String exportService = request.getExportService();
    String importService = request.getImportService();
    Optional<ExportInformation> exportInformation = Optional.ofNullable(request.getExportInformation());
    String exportCallbackUrl = request.getExportCallbackUrl();
    String importCallbackUrl = request.getImportCallbackUrl();

    // Create a new job and persist
    UUID jobId = UUID.randomUUID();
    // 基于AES生成加密的secret
    SecretKey sessionKey = symmetricKeyGenerator.generate();
    String encodedSessionKey = BaseEncoding.base64Url().encode(sessionKey.getEncoded());

    String encryptionScheme = request.getEncryptionScheme();
    PortabilityJob job;
    try {
      job = createJob(encodedSessionKey, dataType, exportService, importService,
              exportInformation, encryptionScheme);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    AuthDataGenerator exportGenerator =
        registry.getAuthDataGenerator(job.exportService(), job.transferDataType(), EXPORT);
    Preconditions.checkNotNull(
        exportGenerator,
        "Generator not found for type: %s, service: %s",
        job.transferDataType(),
        job.exportService());

    AuthDataGenerator importGenerator =
        registry.getAuthDataGenerator(job.importService(), job.transferDataType(), IMPORT);
    Preconditions.checkNotNull(
        importGenerator,
        "Generator not found for type: %s, service: %s",
        job.transferDataType(),
        job.importService());

    try {
      String encodedJobId = encodeJobId(jobId);
      // 导出的身份认证规则
      AuthFlowConfiguration exportConfiguration =
              // 提供一个 authUrl 来重定向用户进行身份验证。在Oauth2的例子中，这是 authUrl 授权代码
          exportGenerator.generateConfiguration(exportCallbackUrl, encodedJobId);

      // 导入的身份认证规则
      AuthFlowConfiguration importConfiguration =
          importGenerator.generateConfiguration(importCallbackUrl, encodedJobId);
      // 初始化授权认证信息
      job = setInitialAuthDataOnJob(sessionKey, job, exportConfiguration, importConfiguration);

      // 创建谷歌云存储任务
      jobStore.createJob(jobId, job);

      monitor.debug(
          () ->
              format(
                  "Created new transfer of type '%s' from '%s' to '%s' with jobId: %s",
                  dataType, exportService, importService, jobId),
          jobId, EventCode.API_JOB_CREATED);

      return new TransferJob(
          encodedJobId,
          job.exportService(),
          job.importService(),
          job.transferDataType(),
          exportConfiguration.getAuthUrl(),
          importConfiguration.getAuthUrl(),
          exportConfiguration.getTokenUrl(),
          importConfiguration.getTokenUrl(),
          exportConfiguration.getAuthProtocol(),
          importConfiguration.getAuthProtocol());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 初始化Google授权认证相关信息
   * @param sessionKey
   * @param job
   * @param exportConfiguration
   * @param importConfiguration
   * @return
   * @throws JsonProcessingException
   */
  private PortabilityJob setInitialAuthDataOnJob(SecretKey sessionKey, PortabilityJob job,
      AuthFlowConfiguration exportConfiguration, AuthFlowConfiguration importConfiguration)
      throws JsonProcessingException {
    // If present, store initial auth data for export services, e.g. used for oauth1
    // 如果存在，则存储用于导出服务的初始身份验证数据，例如用于oauth1
    if (exportConfiguration.getInitialAuthData() != null) {
      // Ensure initial auth data for export has not already been set
      // 确保尚未设置用于导出的初始身份验证数据
      Preconditions.checkState(
          Strings.isNullOrEmpty(job.jobAuthorization().encryptedInitialExportAuthData()));

      // Serialize and encrypt the initial auth data
      // 序列化和加密初始身份验证数据
      String serialized = objectMapper.writeValueAsString(exportConfiguration.getInitialAuthData());
      String encryptedInitialAuthData = encrypterFactory.create(sessionKey).encrypt(serialized);

      // Add the serialized and encrypted initial auth data to the job authorization
      // 将序列化和加密的初始身份验证数据添加到作业授权中
      JobAuthorization updatedJobAuthorization =
          job.jobAuthorization()
              .toBuilder()
              .setEncryptedInitialExportAuthData(encryptedInitialAuthData)
              .build();

      // Persist the updated PortabilityJob with the updated JobAuthorization
      job = job.toBuilder().setAndValidateJobAuthorization(updatedJobAuthorization).build();
    }
    if (importConfiguration.getInitialAuthData() != null) {
      // Ensure initial auth data for import has not already been set
      Preconditions.checkState(
          Strings.isNullOrEmpty(job.jobAuthorization().encryptedInitialImportAuthData()));

      // Serialize and encrypt the initial auth data
      String serialized = objectMapper.writeValueAsString(importConfiguration.getInitialAuthData());
      String encryptedInitialAuthData = encrypterFactory.create(sessionKey).encrypt(serialized);

      // Add the serialized and encrypted initial auth data to the job authorization
      JobAuthorization updatedJobAuthorization =
          job.jobAuthorization()
              .toBuilder()
              .setEncryptedInitialImportAuthData(encryptedInitialAuthData)
              .build();

      // Persist the updated PortabilityJob with the updated JobAuthorization
      job = job.toBuilder().setAndValidateJobAuthorization(updatedJobAuthorization).build();
    }
    return job;
  }

  /**
   * Populates the initial state of the {@link PortabilityJob} instance.
   */
  private PortabilityJob createJob(
      String encodedSessionKey,
      String dataType,
      String exportService,
      String importService,
      Optional<ExportInformation> exportInformation,
      String encryptionScheme) throws IOException {

    // Job auth data
    JobAuthorization jobAuthorization =
        JobAuthorization.builder()
            .setSessionSecretKey(encodedSessionKey)
            .setEncryptionScheme(encryptionScheme)
            .setState(JobAuthorization.State.INITIAL)
            .build();

    PortabilityJob.Builder builder =
        PortabilityJob.builder()
            .setTransferDataType(dataType)
            .setExportService(exportService)
            .setImportService(importService)
            .setAndValidateJobAuthorization(jobAuthorization);
    if (exportInformation.isPresent()) {
      builder.setExportInformation(objectMapper.writeValueAsString(exportInformation.get()));
    }

    return builder.build();
  }
}

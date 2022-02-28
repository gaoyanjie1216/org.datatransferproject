package org.datatransferproject.spi.api.auth.extension;

import java.util.List;
import org.datatransferproject.api.launcher.AbstractExtension;
import org.datatransferproject.spi.api.auth.AuthDataGenerator;
import org.datatransferproject.spi.api.auth.AuthServiceProviderRegistry.AuthMode;

/**
 * Factory responsible for providing {@link AuthDataGenerator} implementations.
 *
 * <p>REVIEW: There is no distinction between offline and online generators since offline input data
 * collection should be externalized from this layer
 *
 * 负责提供{@link AuthDataGenerator}实现的工厂。
 * REVIEW:在线和离线生成器之间没有区别，因为离线输入数据集合应该从这一层具体化
 *
 * auth 扩展只需要为服务编写一次，并且可以被服务支持的每种不同的数据类型重用。
 * 一些常见的身份验证扩展，如 OAuth，已经在项目的库文件夹中可用，并且可以使用非常少的代码（主要是配置）进行扩展。
 * 或者，您可以添加自己的身份验证扩展，只要它实现 AuthServiceExtension 接口即可。
 *
 * 传输扩展由服务的导入适配器和导出适配器组成，每个适配器都用于单一数据类型。
 * 您会发现它们在扩展/数据传输模块中按服务和数据类型进行组织。为了添加一个，您必须添加一个类似的包结构，
 * 并通过使用适配器的相应 AuthData 和 DataModel 类实现 Importer<a extends AuthData, T extends DataModel>
 * 接口来编写您的适配器。
 *
 * 例如，在 Backblaze 中，我们创建了两个导入适配器，一个用于照片，一个用于视频。
 * 他们每个人都使用包含应用程序 key 和 secret 的 TokenSecretAuthData 。
 * 照片导入器使用PhotosContainerResource作为 DataModel，视频导入器使用 VideosContainerResource。
 * 为导入器或导出器准备好样板代码后，您必须根据需要使用任何相关的 SDK 从接口实现所需的方法以使其工作。
 * 由于 Backblaze 提供Backblaze S3 Compatible API，我们能够使用 AWS S3 SDK 来实现 Backblaze 适配器。
 *
 * 身份认证相关
 */
public interface AuthServiceExtension extends AbstractExtension{

  /** Returns the id of the service this factory supports. */
  String getServiceId();

  /**
   * Returns an authentication generator for the given data type.
   *
   * @param transferDataType the data type
   */
  AuthDataGenerator getAuthDataGenerator(String transferDataType, AuthMode mode);

  /**
   * get supported import types
   *
   * @return The list of types that are supported for IMPORT AuthMode
   */
  List<String> getImportTypes();

  /**
   * get supported export types
   *
   * @return The list of types that are supported for EXPORT AuthMode
   */
  List<String> getExportTypes();
}

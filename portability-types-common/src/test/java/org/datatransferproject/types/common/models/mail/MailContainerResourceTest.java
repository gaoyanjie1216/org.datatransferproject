package org.datatransferproject.types.common.models.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.List;
import org.datatransferproject.types.common.models.ContainerResource;
import org.datatransferproject.types.common.models.mail.MailContainerModel;
import org.datatransferproject.types.common.models.mail.MailContainerResource;
import org.datatransferproject.types.common.models.mail.MailMessageModel;
import org.junit.Test;

public class MailContainerResourceTest {
  @Test
  public void verifySerializeDeserialize() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerSubtypes(MailContainerResource.class);

    List<MailContainerModel> containers =
        ImmutableList.of(
            new MailContainerModel("id1", "container1"),
            new MailContainerModel("id2", "container2"));

    // containers: [MailContainerModel{id=id1, name=container1}, MailContainerModel{id=id2, name=container2}]
    System.out.println("containers: " + containers);

    List<MailMessageModel> messages =
        ImmutableList.of(
            new MailMessageModel("foo", ImmutableList.of("1")),
            new MailMessageModel("bar", ImmutableList.of("1", "2'")));

    // messages: [MailMessageModel{rawString=3, containerIds=1}, MailMessageModel{rawString=3, containerIds=2}]
    System.out.println("messages: " + messages);

    ContainerResource data = new MailContainerResource(containers, messages);

    String serialized = objectMapper.writeValueAsString(data);

    // serialized: {"@type":"MailContainerResource","folders":[{"id":"id1","name":"container1"},{"id":"id2","name":"container2"}],
    // "messages":[{"rawString":"foo","containerIds":["1"]},{"rawString":"bar","containerIds":["1","2'"]}],"counts":null}
    System.out.println("serialized: " + serialized);

    ContainerResource deserializedModel =
        objectMapper.readValue(serialized, ContainerResource.class);

    Truth.assertThat(deserializedModel).isNotNull();
    Truth.assertThat(deserializedModel).isInstanceOf(MailContainerResource.class);
    MailContainerResource deserialized = (MailContainerResource) deserializedModel;
    Truth.assertThat(deserialized.getMessages()).hasSize(2);
    Truth.assertThat(deserialized.getFolders()).hasSize(2);
    Truth.assertThat(deserialized).isEqualTo(data);
  }
}

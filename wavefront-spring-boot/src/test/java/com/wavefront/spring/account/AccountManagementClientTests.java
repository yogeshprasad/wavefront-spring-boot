package com.wavefront.spring.account;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.autoconfigure.ApplicationTagsFactory;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Tests for {@link AccountManagementClient}.
 *
 * @author Stephane Nicoll
 */
class AccountManagementClientTests {

  private final MockRestServiceServer mockServer;

  private final AccountManagementClient client;

  AccountManagementClientTests() {
    MockServerRestTemplateCustomizer restTemplateCustomizer = new MockServerRestTemplateCustomizer();
    RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder().customizers(restTemplateCustomizer);
    this.client = new AccountManagementClient(restTemplateBuilder);
    this.mockServer = restTemplateCustomizer.getServer();
  }

  @Test
  void provisionAccountOnSupportedCluster() {
    this.mockServer
        .expect(requestToUriTemplate(
            "https://example.com/api/v2/trial/spring-boot-autoconfigure?source={0}&referer={1}&application={2}&service={3}",
            this.client.getHostName(), "v1.1", "unnamed_application", "unnamed_service"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body("{\"url\":\"/us/test123\",\"token\":\"ee479a71-abcd-abcd-abcd-62b0e8416989\"}\n"));
    AccountInfo accountInfo = this.client.provisionAccount("https://example.com", createDefaultApplicationTags());
    assertThat(accountInfo.getApiToken()).isEqualTo("ee479a71-abcd-abcd-abcd-62b0e8416989");
    assertThat(accountInfo.getLoginUrl()).isEqualTo("https://example.com/us/test123");
  }

  @Test
  void provisionAccountOnSupportedClusterWithCustomInfo() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("wavefront.application.name", "test-application")
        .withProperty("wavefront.application.service", "test-service")
        .withProperty("wavefront.application.cluster", "test-cluster")
        .withProperty("wavefront.application.shard", "test-shard");
    this.mockServer.expect(requestToUriTemplate(
        "https://example.com/api/v2/trial/spring-boot-autoconfigure?source={0}&referer={1}&application={2}&service={3}&cluster={4}&shard={5}",
        this.client.getHostName(), "v1.1", "test-application", "test-service", "test-cluster", "test-shard")).andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body("{\"url\":\"/us/test123\",\"token\":\"ee479a71-abcd-abcd-abcd-62b0e8416989\"}\n"));
    AccountInfo accountInfo = this.client.provisionAccount("https://example.com",
        new ApplicationTagsFactory().createFromEnvironment(environment));
    assertThat(accountInfo.getApiToken()).isEqualTo("ee479a71-abcd-abcd-abcd-62b0e8416989");
    assertThat(accountInfo.getLoginUrl()).isEqualTo("https://example.com/us/test123");
  }

  @Test
  void provisionAccountOnUnsupportedCluster() {
    this.mockServer
        .expect(requestToUriTemplate(
            "https://example.com/api/v2/trial/spring-boot-autoconfigure?source={0}&referer={1}&application={2}&service={3}",
            this.client.getHostName(), "v1.1", "unnamed_application", "unnamed_service"))
        .andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.NOT_ACCEPTABLE)
        .contentType(MediaType.APPLICATION_JSON).body("test failure".getBytes()));
    assertThatThrownBy(() -> this.client.provisionAccount("https://example.com", createDefaultApplicationTags()))
        .hasMessageContaining("test failure").isInstanceOf(AccountManagementFailedException.class);
  }

  @Test
  void retrieveAccountOnSupportedCluster() {
    this.mockServer
        .expect(requestToUriTemplate(
            "https://example.com/api/v2/trial/spring-boot-autoconfigure?source={0}&referer={1}&application={0}&service={1}",
            this.client.getHostName(), "v1.1", "unnamed_application", "unnamed_service"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ee479a71-abcd-abcd-abcd-62b0e8416989"))
        .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body("{\"url\":\"/us/test123\"}\n"));
    AccountInfo accountInfo = this.client.getExistingAccount("https://example.com", createDefaultApplicationTags(),
        "ee479a71-abcd-abcd-abcd-62b0e8416989");
    assertThat(accountInfo.getApiToken()).isEqualTo("ee479a71-abcd-abcd-abcd-62b0e8416989");
    assertThat(accountInfo.getLoginUrl()).isEqualTo("https://example.com/us/test123");
  }

  @Test
  void retrieveAccountWithWrongApiToken() {
    this.mockServer
        .expect(requestToUriTemplate(
            "https://example.com/api/v2/trial/spring-boot-autoconfigure?source={0}&referer={1}&application={0}&service={1}",
            this.client.getHostName(), "v1.1", "unnamed_application", "unnamed_service"))
        .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
            .body("test failure".getBytes()));
    assertThatThrownBy(() -> this.client.getExistingAccount("https://example.com", createDefaultApplicationTags(),
        "wrong-token")).hasMessageContaining("test failure")
        .isInstanceOf(AccountManagementFailedException.class);
  }

  private ApplicationTags createDefaultApplicationTags() {
    return new ApplicationTagsFactory().createFromEnvironment(new MockEnvironment());
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.autoconfigure.resources.OtelResourceAutoConfiguration;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link OpenTelemetryAutoConfiguration}. */
class OpenTelemetryAutoConfigurationTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public OpenTelemetry customOpenTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  @DisplayName(
      "when Application Context contains OpenTelemetry bean should NOT initialize openTelemetry")
  void customOpenTelemetry() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("customOpenTelemetry")
                    .doesNotHaveBean("openTelemetry")
                    .doesNotHaveBean("sdkTracerProvider")
                    .doesNotHaveBean("sdkMeterProvider")
                    .doesNotHaveBean("sdkLoggerProvider"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean should initialize openTelemetry")
  void initializeProvidersAndOpenTelemetry() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("openTelemetry")
                    .hasBean("sdkTracerProvider")
                    .hasBean("sdkMeterProvider")
                    .hasBean("sdkLoggerProvider"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean but TracerProvider should initialize openTelemetry")
  void initializeOpenTelemetryWithCustomProviders() {
    this.contextRunner
        .withBean(
            "customTracerProvider",
            SdkTracerProvider.class,
            () -> SdkTracerProvider.builder().build(),
            bd -> bd.setDestroyMethodName(""))
        .withBean(
            "customMeterProvider",
            SdkMeterProvider.class,
            () -> SdkMeterProvider.builder().build(),
            bd -> bd.setDestroyMethodName(""))
        .withBean(
            "customLoggerProvider",
            SdkLoggerProvider.class,
            () -> SdkLoggerProvider.builder().build(),
            bd -> bd.setDestroyMethodName(""))
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("openTelemetry")
                    .hasBean("customTracerProvider")
                    .doesNotHaveBean("sdkTracerProvider")
                    .hasBean("customMeterProvider")
                    .doesNotHaveBean("sdkMeterProvider")
                    .hasBean("customLoggerProvider")
                    .doesNotHaveBean("sdkLoggerProvider"));
  }

  @Test
  @DisplayName("when otel attributes are set in properties they should be put in resource")
  void shouldInitializeAttributes() {
    this.contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class))
        .withPropertyValues(
            "otel.resource.attributes.xyz=foo",
            "otel.resource.attributes.environment=dev",
            "otel.resource.attributes.service.instance.id=id-example")
        .run(
            context -> {
              Resource otelResource = context.getBean("otelResource", Resource.class);

              assertThat(otelResource.getAttribute(AttributeKey.stringKey("environment")))
                  .isEqualTo("dev");
              assertThat(otelResource.getAttribute(AttributeKey.stringKey("xyz"))).isEqualTo("foo");
              assertThat(otelResource.getAttribute(AttributeKey.stringKey("service.instance.id")))
                  .isEqualTo("id-example");
            });
  }

  @Test
  void shouldInitializeSdkWhenNotDisabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.sdk.disabled=false")
        .run(
            context -> {
              assertThat(context).getBean("openTelemetry").isInstanceOf(OpenTelemetrySdk.class);
              assertThat(context)
                  .hasBean("openTelemetry")
                  .hasBean("sdkTracerProvider")
                  .hasBean("sdkMeterProvider");
            });
  }

  @Test
  void shouldInitializeNoopOpenTelemetryWhenSdkIsDisabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.sdk.disabled=true")
        .run(
            context -> {
              assertThat(context).getBean("openTelemetry").isEqualTo(OpenTelemetry.noop());
              assertThat(context)
                  .doesNotHaveBean("sdkTracerProvider")
                  .doesNotHaveBean("sdkMeterProvider");
            });
  }
}

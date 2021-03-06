/*
 * Copyright 2020 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.decibeltx.studytracker.benchling.eln;

import com.decibeltx.studytracker.benchling.exception.BenchlingException;
import com.decibeltx.studytracker.benchling.exception.BenchlingExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(name = "notebook.mode", havingValue = "benchling")
public class BenchlingElnServiceConfiguration {

  @Autowired
  private Environment env;

  @Bean
  public ObjectMapper BenchlingElnObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public RestTemplate BenchlingElnRestTemplate() {
    RestTemplate restTemplate = new RestTemplateBuilder()
        .errorHandler(new BenchlingExceptionHandler(BenchlingElnObjectMapper()))
        .build();
    MappingJackson2HttpMessageConverter httpMessageConverter = new MappingJackson2HttpMessageConverter();
    httpMessageConverter.setObjectMapper(BenchlingElnObjectMapper());
    restTemplate.getMessageConverters().add(0, httpMessageConverter);
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setOutputStreaming(false);
    restTemplate.setRequestFactory(requestFactory);
    return restTemplate;
  }

  @Bean
  public BenchlingElnOptions elnOptions() throws Exception {
    BenchlingElnOptions options = new BenchlingElnOptions();

    // Authentication
    if (env.containsProperty("benchling.eln.api.token")) {
      Assert.notNull(env.getRequiredProperty("benchling.eln.api.token"),
          "API token must not be null. Eg. benchling.eln.api.token=xxx");
      options.setApiToken(env.getRequiredProperty("benchling.eln.api.token"));
    } else if (env.containsProperty("benchling.eln.api.username") && env
        .containsProperty("benchling.eln.api.password")) {
      Assert.notNull(env.getRequiredProperty("benchling.eln.api.username"),
          "API username must not be null. Eg. benchling.eln.api.username=xxx");
      Assert.notNull(env.getRequiredProperty("benchling.eln.api.password"),
          "API password must not be null. Eg. benchling.eln.api.password=xxx");
      options.setUsername(env.getRequiredProperty("benchling.eln.api.username"));
      options.setPassword(env.getRequiredProperty("benchling.eln.api.password"));
    } else {
      throw new BenchlingException(
          "Missing configuration properties. Authentication requires the 'benchling.eln.api.username' and 'benchling.eln.api.password' properties or the 'benchling.eln.api.token' property.");
    }

    Assert.notNull(env.getProperty("benchling.eln.api.root-url"),
        "benchling ELN API root URL is not set.");
    options.setRootUrl(new URL(env.getRequiredProperty("benchling.eln.api.root-url")));
    Assert.notNull(env.getProperty("benchling.eln.api.root-entity"),
        "benchling ELN API root entity is not set.");
    options.setRootEntity(env.getRequiredProperty("benchling.eln.api.root-entity"));

    //Folder URL
    Assert.notNull(env.getProperty("benchling.eln.api.root-folder-url"),
            "benchling ELN API root entity is not set.");
    options.setRootFolderUrl(env.getRequiredProperty("benchling.eln.api.root-folder-url"));

    return options;
  }

  @Bean
  public BenchlingElnRestClient BenchlingRestElnClient(BenchlingElnOptions options)
      throws Exception {
    return new BenchlingElnRestClient(
        BenchlingElnRestTemplate(),
        options.getRootUrl(),
        options.getApiToken()
    );
  }

  @Bean
  public BenchlingNotebookService benchlingNotebookService() {
    return new BenchlingNotebookService();
  }

}

package org.kgrid.adapter.javascript.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class RepoUtils {


  static ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
  static ObjectMapper yamlMapper = new YAMLMapper();
  static ObjectMapper jsonMapper = new ObjectMapper();

  /*
   ** Loaders for the mock repo
   */
  public static JsonNode getYamlTestFile(String ark, String filePath) throws IOException {

    Resource r = resourceResolver.getResource("/shelf/" + ark + "/" + filePath);

    final JsonNode sd = yamlMapper.readTree(r.getFile());
    return sd;
  }

  public static JsonNode getJsonTestFile(String ark, String filePath) throws IOException {

    Resource r = resourceResolver.getResource("/shelf/" + ark + "/" + filePath);

    final JsonNode sd = jsonMapper.readTree(r.getFile());
    return sd;
  }

  public static byte[] getBinaryTestFile(String ark, String filePath) throws IOException {

    Resource r = resourceResolver.getResource("/shelf/" + ark + "/" + filePath);

    byte[] binary = Files.readAllBytes(r.getFile().toPath());

    return binary;
  }



}

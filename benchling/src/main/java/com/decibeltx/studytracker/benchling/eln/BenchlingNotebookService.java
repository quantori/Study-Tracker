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

import com.decibeltx.studytracker.benchling.eln.entities.BenchlingEntry;
import com.decibeltx.studytracker.benchling.eln.entities.BenchlingFolder;
import com.decibeltx.studytracker.benchling.exception.EntityNotFoundException;
import com.decibeltx.studytracker.core.eln.NotebookEntry;
import com.decibeltx.studytracker.core.eln.NotebookFolder;
import com.decibeltx.studytracker.core.eln.StudyNotebookService;
import com.decibeltx.studytracker.core.exception.NotebookException;
import com.decibeltx.studytracker.core.model.Assay;
import com.decibeltx.studytracker.core.model.Program;
import com.decibeltx.studytracker.core.model.Study;
import com.decibeltx.studytracker.core.service.NamingService;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public final class BenchlingNotebookService implements StudyNotebookService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BenchlingNotebookService.class);

  @Autowired
  private BenchlingElnRestClient client;

  @Autowired
  private BenchlingElnOptions options;

  @Autowired
  private NamingService namingService;

  private String createFolderUrl(BenchlingFolder folder) {
    return options.getRootFolderUrl() + "/" + folder.getId().replace("lib_", "") + "-"
        + folder.getName().toLowerCase()
        .replaceAll(" ", "-")
        .replaceAll("[^A-Za-z0-9-_\\s()]+", "")
        .replaceAll("[\\()]", "")
        .trim();
  }

  private NotebookEntry convertBenchlingEntry(BenchlingEntry benchlingEntry) {
    NotebookEntry notebookEntry = new NotebookEntry();
    notebookEntry.setName(benchlingEntry.getName());
    notebookEntry.setReferenceId(benchlingEntry.getId());
    notebookEntry.setUrl(benchlingEntry.getWebURL());
    notebookEntry.getAttributes().put("folderId", benchlingEntry.getFolderId());
    return notebookEntry;
  }

  private NotebookFolder convertBenchlingFolder(BenchlingFolder benchlingFolder) {
    NotebookFolder notebookFolder = new NotebookFolder();
    notebookFolder.setName(benchlingFolder.getName());
    notebookFolder.setUrl(this.createFolderUrl(benchlingFolder));
    notebookFolder.setReferenceId(benchlingFolder.getId());
    notebookFolder.getAttributes().put("projectId", benchlingFolder.getProjectId());
    return notebookFolder;
  }

  private NotebookFolder convertFolder(BenchlingFolder benchlingFolder) {
    return convertFolder(benchlingFolder, true);
  }

  private NotebookFolder convertFolder(BenchlingFolder benchlingFolder, boolean includeContents) {
    NotebookFolder notebookFolder = convertBenchlingFolder(benchlingFolder);
    if (includeContents) {
      loadContents(benchlingFolder, notebookFolder);
    }
    return notebookFolder;
  }

  private void loadContents(BenchlingFolder benchlingFolder, NotebookFolder notebookFolder) {
    List<BenchlingEntry> projectEntries = client.findProjectEntries(benchlingFolder.getProjectId());
    projectEntries.stream()
            .filter(entry -> entry.getFolderId().equals(benchlingFolder.getId()))
            .forEach(entry -> notebookFolder.getEntries().add(convertBenchlingEntry(entry)));
    List<BenchlingFolder> childrenFolders = client.findFolderChildren(benchlingFolder.getId());
    childrenFolders.forEach(folder -> notebookFolder.getSubFolders().add(convertFolder(folder)));
    Optional<BenchlingFolder> parentBenchlingFolder = client.findFolderById(benchlingFolder.getParentFolderId());
    parentBenchlingFolder.ifPresent(folder -> notebookFolder.setParentFolder(convertBenchlingFolder(folder)));
  }

  private String getNotebookFolderPath(Study study) {
    StringBuilder path = new StringBuilder("/");
    NotebookFolder studyFolder = study.getNotebookFolder();
    Optional<BenchlingFolder> benchlingFolderOptional = client.findFolderById(studyFolder.getReferenceId());
    benchlingFolderOptional
            .flatMap(benchlingFolder -> client.findProjectById(benchlingFolder.getProjectId()))
            .ifPresent(benchlingProject -> path.append(benchlingProject.getName()).append("/"));
    path.append(study.getProgram().getName()).append("/").append(study.getName());
    return path.toString();
  }

  private NotebookFolder getContentFullNotebookFolder(NotebookFolder notebookFolder, Study study) {
    String path = getNotebookFolderPath(study);
    notebookFolder.setPath(path);
    return notebookFolder;
  }

  @Override
  public Optional<NotebookFolder> findProgramFolder(Program program) {

    LOGGER.info("Fetching benchling notebook entry for program: " + program.getName());

    if (program.getNotebookFolder() != null) {
      Optional<BenchlingFolder> optional = client
          .findFolderById(program.getNotebookFolder().getReferenceId());
      return optional.map(this::convertFolder);
    } else {
      LOGGER.warn(
          String.format("Program %s does not have a notebook folder set.", program.getName()));
      return Optional.empty();
    }

  }

  @Override
  public Optional<NotebookFolder> findStudyFolder(Study study) {

    LOGGER.info("Fetching notebook entry for study: " + study.getCode());

    // Does the study have the folder object set?
    if (study.getNotebookFolder() != null) {
      NotebookFolder studyFolder = study.getNotebookFolder();
      Optional<BenchlingFolder> benchlingFolderOptional = client.findFolderById(studyFolder.getReferenceId());
      return benchlingFolderOptional
              .map(this::convertFolder)
              .map(notebookFolder -> getContentFullNotebookFolder(notebookFolder, study));
    } else {
      LOGGER.warn(String.format("Study %s does not have a notebook folder set.", study.getName()));
      return Optional.empty();
    }

  }

  @Override
  public Optional<NotebookFolder> findAssayFolder(Assay assay) {

    LOGGER.info("Fetching notebook entry for assay: " + assay.getCode());

    if (assay.getNotebookFolder() != null) {
      NotebookFolder assayFolder = assay.getNotebookFolder();
      Optional<BenchlingFolder> optional = client.findFolderById(assayFolder.getReferenceId());
      return optional.map(this::convertFolder);
    } else {
      LOGGER.warn(String.format("Assay %s does not have a notebook folder set.", assay.getName()));
      return Optional.empty();
    }

  }

  @Override
  public NotebookFolder createProgramFolder(Program program) throws NotebookException {
    LOGGER.info(
        "Registering new program folder. NOTE: Benchling does not support project creation, so a valid folderId must be provided when registering new programs.");
    if (program.getNotebookFolder() != null
        && program.getNotebookFolder().getReferenceId() != null) {
      try {
        BenchlingFolder folder = client.findFolderById(program.getNotebookFolder().getReferenceId())
            .get();
        return this.convertFolder(folder, false);
      } catch (Exception e) {
        LOGGER.error("Failed to register new program: " + program.getName());
        throw new NotebookException(e);
      }
    } else {
      LOGGER.warn("Program folder ID is not set, cannot create NotebookFolder record for program: "
          + program.getName());
    }
    return null;
  }

  @Override
  public NotebookFolder createStudyFolder(Study study) throws NotebookException {
    LOGGER.info("Creating Benchling folder for study: " + study.getCode());

    Optional<NotebookFolder> programFolderOptional = this.findProgramFolder(study.getProgram());
    if (programFolderOptional.isEmpty()) {
      throw new EntityNotFoundException(
          "Could not find folder for program: " + study.getProgram().getName());
    }
    NotebookFolder programFolder = programFolderOptional.get();

    BenchlingFolder benchlingFolder = client
        .createFolder(namingService.getStudyNotebookFolderName(study),
            programFolder.getReferenceId());
    NotebookFolder studyFolder = this.convertFolder(benchlingFolder, false);
    studyFolder.setParentFolder(programFolder);
    return studyFolder;

  }


  @Override
  public NotebookFolder createAssayFolder(Assay assay) throws NotebookException {
    LOGGER.info("Creating Benchling folder for assay: " + assay.getCode());

    Optional<NotebookFolder> studyFolderOptional = this.findStudyFolder(assay.getStudy());
    if (studyFolderOptional.isEmpty()) {
      throw new EntityNotFoundException(
          "Could not find folder for study: " + assay.getStudy().getCode());
    }
    NotebookFolder studyFolder = studyFolderOptional.get();

    BenchlingFolder benchlingFolder = client
        .createFolder(namingService.getAssayNotebookFolderName(assay),
            studyFolder.getReferenceId());
    NotebookFolder assayFolder = this.convertFolder(benchlingFolder, false);
    assayFolder.setParentFolder(studyFolder);

    return assayFolder;

  }
}

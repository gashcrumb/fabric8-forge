/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.devops;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.forge.addon.utils.dto.OutputFormat;
import io.fabric8.forge.devops.dto.ProjectOverviewDTO;
import io.fabric8.utils.Files;
import io.fabric8.utils.TablePrinter;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.util.ResourceUtil;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UISelection;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.fabric8.forge.addon.utils.CamelProjectHelper.findCamelCoreDependency;
import static io.fabric8.forge.addon.utils.OutputFormatHelper.addTableTextOutput;
import static io.fabric8.forge.addon.utils.OutputFormatHelper.toJson;


public class GetOverviewCommand extends AbstractDevOpsCommand {

    @Inject
    @WithAttributes(label = "Format", defaultValue = "Text", description = "Format output as text or json")
    private UISelectOne<OutputFormat> format;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(GetOverviewCommand.class).name(
                "DevOps: Get Overview").category(Categories.create(CATEGORY))
                .description("Gets the overview of the builders and perspectives for this project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        builder.add(format);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        ProjectOverviewDTO projectOveriew = new ProjectOverviewDTO();
        UIContext uiContext = context.getUIContext();
        File rootFolder = getSelectionFolder(uiContext);
        if (rootFolder != null) {
            List<FileProcessor> processors = loadFileMatches();
            scanProject(rootFolder, processors, projectOveriew, 0, 3);
        }
        if (hasProjectFile(uiContext, "pom.xml")) {
            projectOveriew.addBuilder("maven");
            projectOveriew.addPerspective("forge");

            if (containsProject(uiContext)) {
                Project project = getSelectedProject(context);
                if (findCamelCoreDependency(project) != null) {
                    projectOveriew.addPerspective("camel");
                }
            }
        }
        String result = formatResult(projectOveriew);
        return Results.success(result);
    }

    protected List<FileProcessor> loadFileMatches() {
        List<FileProcessor> answer = new ArrayList<>();
        answer.add(new FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension) {
                           if (Objects.equals(name, "package.json") || Objects.equals(extension, "js")) {
                               overview.addBuilder("node");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension) {
                           if (Objects.equals(extension, "go")) {
                               overview.addBuilder("golang");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension) {
                           if (Objects.equals(name, "Rakefile") || Objects.equals(extension, "rb")) {
                               overview.addBuilder("ruby");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension) {
                           if (Objects.equals(extension, "swift")) {
                               overview.addBuilder("swift");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        answer.add(new FileProcessor() {
                       @Override
                       public boolean processes(ProjectOverviewDTO overview, File file, String name, String extension) {
                           if (Objects.equals(name, "urls.py") || Objects.equals(extension, "wsgi.py")) {
                               overview.addBuilder("django");
                               return true;
                           }
                           return false;
                       }
                   }
        );
        return answer;
    }

    protected void scanProject(File file, List<FileProcessor> processors, ProjectOverviewDTO overview, int level, int maxLevels) {
        if (file.isFile()) {
            String name = file.getName();
            String extension = Files.getExtension(name);
            for (FileProcessor processor : new ArrayList<>(processors)) {
                if (processor.processes(overview, file, name, extension)) {
                    processors.remove(processor);
                }
            }
        } else if (file.isDirectory()) {
            int newLevel = level + 1;
            if (newLevel <= maxLevels && !processors.isEmpty()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File child : files) {
                        scanProject(child, processors, overview, newLevel, maxLevels);
                    }
                }
            }
        }
    }

    protected interface FileProcessor {
        boolean processes(ProjectOverviewDTO overview, File file, String name, String extension);
    }

    protected boolean hasProjectFile(UIContext context, String fileName) {
        UISelection<Object> selection = context.getSelection();
        if (selection != null) {
            Object object = selection.get();
            if (object instanceof Resource) {
                File folder = ResourceUtil.getContextFile((Resource<?>) object);
                if (folder != null && Files.isDirectory(folder)) {
                    File file = new File(folder, fileName);
                    return file != null && file.exists() && file.isFile();
                }
            }
        }
        return false;
    }

    protected File getSelectionFolder(UIContext context) {
        UISelection<Object> selection = context.getSelection();
        if (selection != null) {
            Object object = selection.get();
            if (object instanceof Resource) {
                File folder = ResourceUtil.getContextFile((Resource<?>) object);
                if (folder != null && Files.isDirectory(folder)) {
                    return folder;
                }
            }
        }
        return null;
    }

    protected String formatResult(ProjectOverviewDTO result) throws JsonProcessingException {
        OutputFormat outputFormat = format.getValue();
        switch (outputFormat) {
            case JSON:
                return toJson(result);
            default:
                return textResult(result);
        }
    }

    protected String textResult(ProjectOverviewDTO project) {
        StringBuilder buffer = new StringBuilder("\n\n");

        Set<String> perspectives = project.getPerspectives();
        TablePrinter table = new TablePrinter();
        table.columns("perspective");
        for (String perspective : perspectives) {
            table.row(perspective);
        }
        addTableTextOutput(buffer, null, table);

        Set<String> builders = project.getBuilders();
        table = new TablePrinter();
        table.columns("builder");
        for (String builder : builders) {
            table.row(builder);
        }
        addTableTextOutput(buffer, null, table);
        return buffer.toString();
    }

}

package io.fabric8.forge.rest;


import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.Addon;
import org.jboss.forge.furnace.addons.AddonId;
import org.jboss.forge.furnace.addons.AddonRegistry;
import org.jboss.forge.furnace.manager.AddonManager;
import org.jboss.forge.furnace.repositories.AddonRepositoryMode;
import org.jboss.forge.furnace.services.Imported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("/repo")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class LocalRepoResource {
    private static final transient Logger LOG = LoggerFactory.getLogger(LocalRepoResource.class);

    @Inject
    private Furnace furnace;

    @PUT
    @Path("{path: .*}")
    public Response putRepoFiles(@PathParam("path") String path, @Multipart(type = "application/octet-stream") InputStream inputStream, @Context HttpServletRequest request) throws IOException {
        return handleUpload(path, inputStream, request);
    }

    @POST
    @Path("{path: .*}")
    public Response postRepoFiles(@PathParam("path") String path, @Multipart(type = "application/octet-stream") InputStream inputStream, @Context HttpServletRequest request) throws IOException {
        return handleUpload(path, inputStream, request);
    }

    private Response handleUpload(String path, InputStream inputStream, HttpServletRequest request) throws IOException {
        String addonDir = getAddonDirectory();
        String addonPath = pathToAddonDir(path);
        String fileName = pathToAddonFile(path);
        String fileDir = Paths.get(addonDir, addonPath).toString();
        String fullPath = Paths.get(fileDir, fileName).toString();
        File dir = new File(fileDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                LOG.debug("Failed to create directory: ", dir.getAbsolutePath());
            }
        }
        FileOutputStream outputStream = new FileOutputStream(fullPath);
        boolean fileWritten = false;
        try {
            LOG.debug("Writing to file: {}", fullPath);
            IOUtils.copy(inputStream, outputStream);
            fileWritten = true;
        } catch (IOException e) {
            LOG.info("Failed to write to file: " + fullPath + " error: ", e);
        } finally {
            outputStream.close();
        }
        if (fileWritten && fileName.endsWith("forge-addon.jar")) {
            AddonRegistry addonRegistry = furnace.getAddonRegistry();
            final AddonManager addonManager = addonRegistry.getServices(AddonManager.class).get();
            String coordinates = fileNameToCoordinates(addonPath, fileName);
            Set<Addon> addons = addonRegistry.getAddons(addon -> {
                String addonCoordinates = addon.getId().toCoordinates();
                return addonCoordinates.equals(coordinates);
            });
            addons.forEach(addon -> {
                AddonId id = addon.getId();
                LOG.info("disabling {}", id);
                addonManager.disable(id).perform();
                LOG.info("removing {}", id);
                addonManager.remove(id).perform();
                LOG.info("installing {}", id);
                addonManager.install(id).perform();
                LOG.info("updated {}", id);
            });
        }
        return Response.ok().build();
    }

    private String fileNameToCoordinates(String path, String fileName) {
        String answer = "";
        java.nio.file.Path asPath = Paths.get(path);
        Iterator<java.nio.file.Path> iter = asPath.iterator();
        String coords = "";
        String version = "";
        String addonName = "";
        while (iter.hasNext()) {
            java.nio.file.Path part = iter.next();
            if (asPath.startsWith(part)) {
                coords = part.toString();
                continue;
            }
            if (fileName.startsWith(part.toString())) {
                addonName = part.toString();
                continue;
            }
            if (asPath.endsWith(part)) {
                version = part.toString();
                continue;
            }
            coords = coords + "." + part.toString();
        }
        answer = coords + ":" + addonName + "," + version;
        return answer;
    }

    @Path("{path: .*}")
    @GET
    public Response getRepoFiles(@PathParam("path") String path) {
        String addonDir = getAddonDirectory();
        String addon = pathToAddonDir(path);
        String fileName = pathToAddonFile(path);
        String fullPath = Paths.get(addonDir, addon, fileName).toString();
        FileInputStream inputStream = null;
        try {
            LOG.debug("Returning contents of file: {}", fullPath);
            inputStream = new FileInputStream(fullPath);
            return Response.ok(inputStream, MediaType.APPLICATION_OCTET_STREAM).build();
        } catch (FileNotFoundException e) {
            LOG.debug("Failed to find file: {}", fullPath);
            return Response.status(404).build();
        }
    }

    private String getAddonDirectory() {
        String home = System.getProperty("user.home", "~/");
        return Paths.get(home, ".m2", "repository").toString();
    }

    private String pathToAddonDir(String path) {
        File parent = new File(path).getParentFile();
        String parentPath = parent.getPath();
        return parentPath;
    }
    private String pathToAddonFile(String path) {
        return new File(path).getName().replaceAll("-[0-9]\\w+\\.[0-9]\\w+-[0-9]+", "-SNAPSHOT");
    }

}

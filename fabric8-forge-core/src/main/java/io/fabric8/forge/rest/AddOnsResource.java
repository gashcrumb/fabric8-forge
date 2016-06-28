package io.fabric8.forge.rest;


import io.fabric8.forge.rest.main.ForgeInitialiser;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("/repo")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class AddOnsResource {
    private static final transient Logger LOG = LoggerFactory.getLogger(AddOnsResource.class);

    @Inject
    private ForgeInitialiser initialiser;

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
        String addon = pathToAddonDir(path);
        String fileName = pathToAddonFile(path);
        String fileDir = Paths.get(addonDir, addon).toString();
        String fullPath = Paths.get(fileDir, fileName).toString();
        File dir = new File(fileDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                LOG.debug("Failed to create directory: ", dir.getAbsolutePath());

            }
        }
        FileOutputStream outputStream = new FileOutputStream(fullPath);
        try {
            LOG.debug("Writing to file: {}", fullPath);
            IOUtils.copy(inputStream, outputStream);
        } catch (IOException e) {
            LOG.info("Failed to write to file: " + fullPath + " error: ", e);
        } finally {
            outputStream.close();
        }
        return Response.ok().build();
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
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private String getAddonDirectory() {
        return this.initialiser.getAddOnDir();
    }

    private String pathToAddonDir(String path) {
        File parent = new File(path).getParentFile();
        String parentPath = parent.getPath().replace(File.separatorChar, '-').replace('.', '-');
        return parentPath;
    }
    private String pathToAddonFile(String path) {
        return new File(path).getName();
    }

}

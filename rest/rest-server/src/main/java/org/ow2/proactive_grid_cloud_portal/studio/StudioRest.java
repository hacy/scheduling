/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.ow2.proactive_grid_cloud_portal.studio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.security.auth.login.LoginException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.core.node.NodeException;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.scheduler.common.Scheduler;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.ow2.proactive.scheduler.common.exception.PermissionException;
import org.ow2.proactive_grid_cloud_portal.common.SchedulerRestInterface;
import org.ow2.proactive_grid_cloud_portal.common.Session;
import org.ow2.proactive_grid_cloud_portal.common.SharedSessionStore;
import org.ow2.proactive_grid_cloud_portal.common.dto.LoginForm;
import org.ow2.proactive_grid_cloud_portal.scheduler.SchedulerStateRest;
import org.ow2.proactive_grid_cloud_portal.scheduler.dto.JobIdData;
import org.ow2.proactive_grid_cloud_portal.scheduler.dto.JobValidationData;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.JobCreationRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.NotConnectedRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.PermissionRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.SchedulerRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.SubmissionClosedRestException;
import org.ow2.proactive_grid_cloud_portal.webapp.PortalConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;


@Path("/studio")
public class StudioRest implements StudioInterface {

    private static String PROJECT_NAME_PROPERTY = "proactive.projects.dir";
    private final static Logger logger = Logger.getLogger(StudioRest.class);
    private SchedulerStateRest schedulerRest = null;

    private SchedulerRestInterface scheduler() {
        if (schedulerRest == null) {
            schedulerRest = new SchedulerStateRest();
        }

        return schedulerRest;
    }

    private String getProjectsDirName() {
        String projectDir = System.getProperty(PROJECT_NAME_PROPERTY);
        if (projectDir == null) {
            projectDir = System.getProperty("java.io.tmpdir");
        }

        return projectDir;
    }

    private String getUserName(String sessionId) throws NotConnectedException {
        Session ss = SharedSessionStore.getInstance().get(sessionId);
        if (ss == null) {
            // logger.trace("not found a scheduler frontend for sessionId " +
            // sessionId);
            throw new NotConnectedException("you are not connected to the scheduler, you should log on first");
        }
        return ss.getUserName();
    }

    private void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                logger.info("Deleting file " + c.getAbsolutePath());
                delete(c);
            }
        }

        logger.info("Deleting file " + f.getAbsolutePath());
        if (!f.delete()) {
            throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }

    private void writeFileContent(String fileName, String content) {
        try {
            logger.info("Writing file " + fileName);
            FileOutputStream output = new FileOutputStream(new File(fileName));
            IOUtils.write(content, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileContent(String fileName) {
        try {
            FileInputStream inputStream = new FileInputStream(fileName);
            return IOUtils.toString(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    @Override
    public String login(@FormParam("username")
    String username, @FormParam("password")
    String password) throws KeyException, LoginException, RMException, ActiveObjectCreationException,
            NodeException, SchedulerRestException {
        logger.info("Logging as " + username);
        return scheduler().login(username, password);
    }

    @Override
    public String loginWithCredential(@MultipartForm
    LoginForm multipart) throws ActiveObjectCreationException, NodeException, KeyException, IOException,
            LoginException, RMException, SchedulerRestException {
        logger.info("Logging using credential file");
        return scheduler().loginWithCredential(multipart);
    }

    @PUT
    @Path("logout")
    @Produces("application/json")
    public void logout(@HeaderParam(
      "sessionid") final String sessionId) throws PermissionRestException, NotConnectedRestException {
        logger.info("logout");
        scheduler().disconnect(sessionId);
    }

    @Override
    @GET
    @Path("connected")
    @Produces("application/json")
    public boolean isConnected(@HeaderParam("sessionid")
    String sessionId) {
        try {
            getUserName(sessionId);
            return true;
        } catch (NotConnectedException e) {
            return false;
        }
    }

    @Override
    @GET
    @Path("workflows")
    @Produces("application/json")
    public ArrayList<Workflow> getWorkflows(@HeaderParam("sessionid")
    String sessionId) throws NotConnectedException {
        String userName = getUserName(sessionId);
        File workflowsDir = new File(getProjectsDirName() + "/" + userName + "/workflows");

        if (!workflowsDir.exists()) {
            logger.info("Creating dir " + workflowsDir.getAbsolutePath());
            workflowsDir.mkdirs();
        }

        logger.info("Getting workflows as " + userName);
        ArrayList<Workflow> projects = new ArrayList<Workflow>();
        for (File f : workflowsDir.listFiles()) {
            if (f.isDirectory()) {
                File nameFile = new File(f.getAbsolutePath() + "/name");

                if (nameFile.exists()) {

                    Workflow wf = new Workflow();
                    wf.setId(Integer.parseInt(f.getName()));
                    wf.setName(getFileContent(nameFile.getAbsolutePath()));

                    File xmlFile = new File(f.getAbsolutePath() + "/" + wf.getName() + ".xml");
                    if (xmlFile.exists()) {
                        wf.setXml(getFileContent(xmlFile.getAbsolutePath()));
                    }
                    File metadataFile = new File(f.getAbsolutePath() + "/metadata");
                    if (metadataFile.exists()) {
                        wf.setMetadata(getFileContent(metadataFile.getAbsolutePath()));
                    }

                    projects.add(wf);
                }
            }
        }

        logger.info(projects.size() + " workflows found");
        return projects;
    }

    @Override
    @POST
    @Path("workflows")
    @Produces("application/json")
    public long createWorkflow(@HeaderParam("sessionid")
    String sessionId, @FormParam("name")
    String name, @FormParam("xml")
    String xml, @FormParam("metadata")
    String metadata) throws NotConnectedException {
        String userName = getUserName(sessionId);

        logger.info("Creating workflow as " + userName);
        File workflowsDir = new File(getProjectsDirName() + "/" + userName + "/workflows");

        if (!workflowsDir.exists()) {
            logger.info("Creating dir " + workflowsDir.getAbsolutePath());
            workflowsDir.mkdirs();
        }

        int projectId = 1;
        while (new File(workflowsDir.getAbsolutePath() + "/" + projectId).exists()) {
            projectId++;
        }

        File newWorkflowFile = new File(workflowsDir.getAbsolutePath() + "/" + projectId);
        logger.info("Creating dir " + newWorkflowFile.getAbsolutePath());
        newWorkflowFile.mkdirs();

        writeFileContent(newWorkflowFile.getAbsolutePath() + "/name", name);
        writeFileContent(newWorkflowFile.getAbsolutePath() + "/metadata", metadata);
        writeFileContent(newWorkflowFile.getAbsolutePath() + "/" + name + ".xml", xml);

        return projectId;
    }

    @Override
    @POST
    @Path("workflows/{id}")
    @Produces("application/json")
    public boolean updateWorkflow(@HeaderParam("sessionid")
    String sessionId, @PathParam("id")
    String workflowId, @FormParam("name")
    String name, @FormParam("xml")
    String xml, @FormParam("metadata")
    String metadata) throws NotConnectedException, IOException {
        String userName = getUserName(sessionId);

        logger.info("Updating workflow " + workflowId + " as " + userName);
        File workflowsDir = new File(getProjectsDirName() + "/" + userName + "/workflows/" + workflowId);

        String oldJobName = getFileContent(workflowsDir.getAbsolutePath() + "/name");
        if (name != null && !name.equals(oldJobName)) {
            // new job name
            logger.info("Updating job name from " + oldJobName + " to " + name);
            writeFileContent(workflowsDir.getAbsolutePath() + "/name", name);
            delete(new File(workflowsDir.getAbsolutePath() + "/" + oldJobName + ".xml"));
        }

        writeFileContent(workflowsDir.getAbsolutePath() + "/metadata", metadata);
        writeFileContent(workflowsDir.getAbsolutePath() + "/" + name + ".xml", xml);

        return true;
    }

    @Override
    @DELETE
    @Path("workflows/{id}")
    @Produces("application/json")
    public boolean deleteWorkflow(@HeaderParam("sessionid")
    String sessionId, @PathParam("id")
    String workflowId) throws NotConnectedException, IOException {
        String userName = getUserName(sessionId);

        logger.info("Deleting workflow " + workflowId + " as " + userName);
        File workflowsDir = new File(getProjectsDirName() + "/" + userName + "/workflows/" + workflowId);

        if (workflowsDir.exists()) {
            delete(workflowsDir);
            return true;
        }
        return false;
    }

    @Override
    @GET
    @Path("scripts")
    @Produces("application/json")
    public ArrayList<Script> getScripts(@HeaderParam("sessionid")
    String sessionId) throws NotConnectedException {
        String userName = getUserName(sessionId);
        File scriptDir = new File(getProjectsDirName() + "/" + userName + "/scripts");

        if (!scriptDir.exists()) {
            logger.info("Creating dir " + scriptDir.getAbsolutePath());
            scriptDir.mkdirs();
        }

        ArrayList<Script> scripts = new ArrayList<Script>();
        for (File f : scriptDir.listFiles()) {

            Script script = new Script();

            script.setName(f.getName());
            script.setAbsolutePath(f.getAbsolutePath());
            script.setContent(getFileContent(f.getAbsolutePath()));
            scripts.add(script);
        }

        logger.info(scripts.size() + " scripts found");
        return scripts;
    }

    @Override
    @POST
    @Path("scripts")
    @Produces("application/json")
    public String createScript(@HeaderParam("sessionid")
    String sessionId, @FormParam("name")
    String name, @FormParam("content")
    String content) throws NotConnectedException {
        String userName = getUserName(sessionId);
        logger.info("Creating script " + name + " as " + userName);
        File scriptDir = new File(getProjectsDirName() + "/" + userName + "/scripts");
        String fileName = scriptDir.getAbsolutePath() + "/" + name;
        writeFileContent(fileName, content);
        return fileName;
    }

    @Override
    @POST
    @Path("scripts/{name}")
    @Produces("application/json")
    public String updateScript(@HeaderParam("sessionid")
    String sessionId, @PathParam("name")
    String name, @FormParam("content")
    String content) throws NotConnectedException {

        return createScript(sessionId, name, content);
    }

    @Override
    @GET
    @Path("classes")
    @Produces("application/json")
    public ArrayList<String> getClasses(@HeaderParam("sessionid")
    String sessionId) throws NotConnectedException {
        String userName = getUserName(sessionId);
        File classesDir = new File(getProjectsDirName() + "/" + userName + "/classes");

        ArrayList<String> classes = new ArrayList<String>();
        if (classesDir.exists()) {
            File[] jars = classesDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".jar");
                }
            });

            for (File jar : jars) {
                try {
                    JarFile jarFile = new JarFile(jar.getAbsolutePath());
                    Enumeration allEntries = jarFile.entries();
                    while (allEntries.hasMoreElements()) {
                        JarEntry entry = (JarEntry) allEntries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".class")) {
                            String noExt = name.substring(0, name.length() - ".class".length());
                            classes.add(noExt.replaceAll("/", "."));
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return classes;

    }

    @POST
    @Path("classes")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    public String createClass(@HeaderParam("sessionid")
    String sessionId, MultipartFormDataInput input) throws NotConnectedException, IOException {

        String userName = getUserName(sessionId);
        File classesDir = new File(getProjectsDirName() + "/" + userName + "/classes");

        if (!classesDir.exists()) {
            logger.info("Creating dir " + classesDir.getAbsolutePath());
            classesDir.mkdirs();
        }

        String fileName = "";

        Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
        String name = uploadForm.keySet().iterator().next();
        List<InputPart> inputParts = uploadForm.get(name);

        for (InputPart inputPart : inputParts) {

            try {
                //convert the uploaded file to inputstream
                InputStream inputStream = inputPart.getBody(InputStream.class, null);
                byte[] bytes = IOUtils.toByteArray(inputStream);

                //constructs upload file path
                fileName = classesDir.getAbsolutePath() + "/" + name;

                writeFile(bytes, fileName);
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            }

        }

        return fileName;
    }

    private void writeFile(byte[] content, String filename) throws IOException {

        File file = new File(filename);

        if (!file.exists()) {
            file.createNewFile();
        }

        FileOutputStream fop = new FileOutputStream(file);

        try {
            fop.write(content);
        } finally {
            fop.flush();
            fop.close();
        }
    }

    @Override
    public JobValidationData validate(MultipartFormDataInput multipart) {
        return scheduler().validate(multipart);
    }

    @Override
    public JobIdData submit(@HeaderParam("sessionid")
    String sessionId, MultipartFormDataInput multipart) throws IOException, JobCreationRestException,
            NotConnectedRestException, PermissionRestException, SubmissionClosedRestException {
        return scheduler().submit(sessionId, multipart);
    }

    @GET
    @Path("visualizations/{id}")
    @Produces("application/json")
    public String getVisualization(@HeaderParam("sessionid")
    String sessionId, @PathParam("id")
    String jobId) throws NotConnectedException {
        File visualizationFile = new File(PortalConfiguration.jobIdToPath(jobId) + ".html");
        if (visualizationFile.exists()) {
            return getFileContent(visualizationFile.getAbsolutePath());
        }
        return "";
    }

    @POST
    @Path("visualizations/{id}")
    @Produces("application/json")
    public boolean updateVisualization(@HeaderParam("sessionid")
    String sessionId, @PathParam("id")
    String jobId, @FormParam("visualization")
    String visualization) throws NotConnectedException {
        File visualizationFile = new File(PortalConfiguration.jobIdToPath(jobId) + ".html");
        if (visualizationFile.exists()) {
            visualizationFile.delete();
        }
        writeFileContent(visualizationFile.getAbsolutePath(), visualization);
        return true;
    }
}
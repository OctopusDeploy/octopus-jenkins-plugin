package hudson.plugins.octopusdeploy;

import com.octopusdeploy.api.data.Project;
import com.octopusdeploy.api.data.Release;
import com.octopusdeploy.api.*;
import hudson.util.FormValidation;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Validations on input for OctopusDeploy.
 */
public class OctopusValidator {
    private final OctopusApi api;
    
    public OctopusValidator(OctopusApi api) {
        this.api = api;
    }

    /**
     * Provides validation on a Project.
     * Validates:
     *  Project is not empty.
     *  Project exists in Octopus.
     *  Project is appropriate case.
     * @param projectName name of the project to validate.
     * @return a form validation.
     */
    public FormValidation validateProject(String projectName) {
        if (projectName.isEmpty()) {
            return FormValidation.error("Please provide a project name.");
        }
        try {
            com.octopusdeploy.api.data.Project p = api.getProjectsApi().getProjectByName(projectName, true);
            if (p == null)
            {
                return FormValidation.error("Project not found.");
            }
            if (!projectName.equals(p.getName()))
            {
                return FormValidation.warning("Project name case does not match. Did you mean '%s'?", p.getName());
            }
        } catch (IllegalArgumentException ex) {
            return FormValidation.error(ex.getMessage());
        } catch (IOException ex) {
            return FormValidation.error(ex.getMessage());
        }
        return FormValidation.ok();
    }
    
    /**
     * Provides validation on a Channel.
     * Validates:
     *  Project is not empty.
     *  Project exists in Octopus.
     *  Project is appropriate case.
     *  Channel is either empty or exists in Octopus
     * @param channelName name of the channel to validate
     * @param projectName name of the project to validate.
     * @return a form validation.
     */
    public FormValidation validateChannel(String channelName, String projectName) {
        if (channelName != null && !channelName.isEmpty()) {
            if (projectName == null || projectName.isEmpty()) {
                return FormValidation.warning("Project must be set to validate this field.");
            }
            com.octopusdeploy.api.data.Project project;
            com.octopusdeploy.api.data.Channel channel;
            try {
                project = api.getProjectsApi().getProjectByName(projectName);
                if (project != null) {
                    channel = api.getChannelsApi().getChannelByName(project.getId(), channelName);
                    if (channel == null) {
                        return FormValidation.error("Channel not found.");
                    }
                }
                else
                {
                    return FormValidation.warning("Project must be set to validate this field.");
                }
            } catch (IllegalArgumentException ex) {
                return FormValidation.warning("Unable to validate field - " + ex.getMessage());
            } catch (IOException ex) {
                return FormValidation.warning("Unable to validate field - " + ex.getMessage());
            }
        }
        return FormValidation.ok();
    }
    
    /**
     * Provides validation on an environment.
     * Validates:
     *  Environment is not empty.
     *  Environment exists in Octopus.
     *  Environment is appropriate case.
     * @param environmentName the name of the environment to validate.
     * @return a form validation.
     */
    public FormValidation validateEnvironment(String environmentName) {
        if (environmentName.isEmpty()) {
            return FormValidation.error("Please provide an environment name.");
        }
        try {
            com.octopusdeploy.api.data.Environment env = api.getEnvironmentsApi().getEnvironmentByName(environmentName, true);
            if (env == null)
            {
                return FormValidation.error("The '%s' environment was not found.", environmentName);
            }
            if (!environmentName.equals(env.getName()))
            {
                return FormValidation.warning("Environment name case does not match. Did you mean '%s'?", env.getName());
            }
        } catch (IllegalArgumentException ex) {
            return FormValidation.error(ex.getMessage());
        } catch (IOException ex) {
            return FormValidation.error(ex.getMessage());
        }
        return FormValidation.ok();
    }
    
    /**
     * Provides validation on releases.
     * Validates:
     *  The project is set.
     *  The release is not empty.
     *  The release conforms to the existence check requirement.
     * @param releaseVersion the release version.
     * @param projectId the project's Id that this release is for.
     * @param existanceCheckReq the requirement for the existence of the release.
     * @return FormValidation response
     */
    public FormValidation validateRelease(String releaseVersion, Project project, ReleaseExistenceRequirement existanceCheckReq) {
        if (releaseVersion.isEmpty()) {
            return FormValidation.error("Please provide a release version.");
        }
        try {
            Set<Release> releases = api.getReleasesApi().getReleasesForProject(project.getId());

            boolean found = false;
            for (Release release : releases) {
                if (releaseVersion.equals(release.getVersion()) ) {
                    found = true;
                    break;
                }
            }
            if (found && existanceCheckReq == ReleaseExistenceRequirement.MustNotExist) {
                return FormValidation.error("Release %s already exists for project '%s'!", releaseVersion, project.getName());
            }
            if (!found && existanceCheckReq == ReleaseExistenceRequirement.MustExist) {
                return FormValidation.error("Release %s doesn't exist for project '%s'!", releaseVersion, project.getName());
            }
        } catch (IllegalArgumentException ex) {
            return FormValidation.error(ex.getMessage());
        } catch (IOException ex) {
            return FormValidation.error(ex.getMessage());
        }
        return FormValidation.ok();
    }

    public static FormValidation validateServerId(String serverId) {
        if (serverId==null || serverId.isEmpty()) {
            return FormValidation.error("Please select an instance of Octopus Deploy.");
        }
        if(serverId.equals("default")) {
            return FormValidation.ok();
        }
        List<String> ids = AbstractOctopusDeployRecorder.getOctopusDeployServersIds();
        if (ids.isEmpty()){
            return FormValidation.error("There are no OctopusDeploy servers configured.");
        }
        if (!ids.contains(serverId)) {
            return FormValidation.error("There are no OctopusDeploy servers configured with this Server Id.");
        }
        return FormValidation.ok();
    }

    /**
     * Whether or not a release must exist or must not exist depending on the operation being done.
     */
    public enum ReleaseExistenceRequirement {
        MustExist, MustNotExist
    }

    public static FormValidation validateDeploymentTimeout(String deploymentTimeout) {
        if (deploymentTimeout != null) {
            deploymentTimeout = deploymentTimeout.trim();
            if (!deploymentTimeout.isEmpty() && !isValidTimeSpan(deploymentTimeout)) {
                return FormValidation.error("This is not a valid deployment timeout it should be in the format HH:mm:ss");
            }
        }

        return FormValidation.ok();
    }

    public static Boolean isValidTimeSpan(String deploymentTimeout)
    {
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
            dtf.parse(deploymentTimeout);
        } catch (DateTimeParseException ex) {
            return false;
        }
        return true;
    }
}

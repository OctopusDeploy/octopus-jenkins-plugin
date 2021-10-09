package com.octopus.helper;

import com.octopus.sdk.Repository;
import com.octopus.sdk.domain.Channel;
import com.octopus.sdk.domain.Environment;
import com.octopus.sdk.domain.Project;
import com.octopus.sdk.domain.ProjectGroup;
import com.octopus.sdk.domain.Space;
import com.octopus.sdk.http.OctopusClient;
import com.octopus.sdk.model.channel.ChannelResource;
import com.octopus.sdk.model.environment.EnvironmentResourceWithLinks;
import com.octopus.sdk.model.project.ProjectResource;
import com.octopus.sdk.model.projectgroup.ProjectGroupResource;
import com.octopus.sdk.model.space.SpaceHome;

import java.io.IOException;

public class SpaceScopedClient {
    private final OctopusClient client;
    private final Repository repository;
    private final Space space;
    private final SpaceHome spaceHome;

    public SpaceScopedClient(final OctopusClient client,
                             final Repository repository,
                             final Space space,
                             final SpaceHome spaceHome) {
        this.client = client;
        this.repository = repository;
        this.space = space;
        this.spaceHome = spaceHome;
    }

    @SuppressWarnings("unused")
    public OctopusClient getClient() {
        return client;
    }

    @SuppressWarnings("unused")
    public SpaceHome getSpaceHome() {
        return spaceHome;
    }

    public Repository getRepository() {
        return repository;
    }

    public Space getSpace() {
        return space;
    }

    public String getSpaceId() {
        return space.getProperties().getId();
    }

    @SuppressWarnings("UnusedReturnValue")
    public Environment createEnvironment(final String environmentName) {
        try {
            return space.environments().create(new EnvironmentResourceWithLinks(environmentName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ProjectGroup createProjectGroup(final String projectGroupName) {
        try {
            return space.projectGroups().create(new ProjectGroupResource(projectGroupName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Project createProject(String projectName, String existingProjectGroupId) {
        try {
            return space.projects().create(new ProjectResource(projectName, "Lifecycle-1", existingProjectGroupId));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Channel createChannel(final String channelName, final String existingProjectId) {
        try {
            return space.channels().create(new ChannelResource(channelName, existingProjectId));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}

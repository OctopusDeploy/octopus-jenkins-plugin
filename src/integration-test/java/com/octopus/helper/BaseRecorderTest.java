package com.octopus.helper;

import com.octopus.testsupport.BaseOctopusServerEnabledTest;
import hudson.plugins.octopusdeploy.AbstractOctopusDeployRecorderPostBuildStep;
import hudson.plugins.octopusdeploy.OctopusDeployServer;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseRecorderTest extends BaseOctopusServerEnabledTest {

    public static final String JENKINS_OCTOPUS_SERVER_ID = "default";

    private MockedStatic<AbstractOctopusDeployRecorderPostBuildStep> postBuildStepMockedStatic;
    private MockedStatic<Jenkins> jenkinsMockedStatic;
    public SpaceScopedClient spaceScopedClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp(final TestInfo testInfo) throws IOException {
        spaceScopedClient = TestHelper.buildSpaceScopedClientForTesting(httpClient,
                server,
                TestHelper.generateSpaceName(testInfo.getDisplayName()));

        postBuildStepMockedStatic = Mockito.mockStatic(AbstractOctopusDeployRecorderPostBuildStep.class);
        postBuildStepMockedStatic
                .when(() ->
                        AbstractOctopusDeployRecorderPostBuildStep.getOctopusDeployServer(JENKINS_OCTOPUS_SERVER_ID))
                .thenReturn(new OctopusDeployServer(JENKINS_OCTOPUS_SERVER_ID,
                        server.getOctopusUrl(), server.getApiKey(), true));

        final Path createdFile = Files.createFile(tempDir.resolve("temp.xml"));
        final Jenkins jenkins = mock(Jenkins.class);
        jenkinsMockedStatic = Mockito.mockStatic(Jenkins.class);

        jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkins);
        when(jenkins.getRootDir()).thenReturn(createdFile.toFile());
        when(Jenkins.getInstanceOrNull()).thenReturn(jenkins);
    }

    @AfterEach
    public void cleanUp() {
        postBuildStepMockedStatic.close();
        jenkinsMockedStatic.close();
        TestHelper.deleteTestingSpace(spaceScopedClient);
    }
}

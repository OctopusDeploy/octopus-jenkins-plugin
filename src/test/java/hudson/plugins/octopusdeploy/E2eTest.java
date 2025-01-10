package hudson.plugins.octopusdeploy;

import com.octopus.sdk.Repository;
import com.octopus.sdk.domain.BuildInformation;
import com.octopus.sdk.domain.Space;
import com.octopus.sdk.http.InMemoryCookieJar;
import com.octopus.sdk.http.OctopusClient;
import com.octopus.testsupport.DockerisedOctopusDeployServer;
import com.octopus.testsupport.ExistingOctopusDeployServer;
import com.octopus.testsupport.OctopusDeployServerFactory;
import hudson.model.FreeStyleProject;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static com.octopus.testsupport.DockerisedOctopusDeployServer.*;
import static org.assertj.core.api.Assertions.assertThat;

/*
E2E Tests run under JUnit-4 and test must be located in the
default Maven test path due to Jenkins test harness limitations.
 */
public class E2eTest {

    private final com.octopus.testsupport.OctopusDeployServer server = makeServer();
    private Space space;

    @Rule
    public final JenkinsRule jenkinsRule = new JenkinsRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    public E2eTest() throws IOException {
    }

    public com.octopus.testsupport.DockerisedOctopusDeployServer makeServer() throws IOException {
        try {
            return createOctopusServer();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static com.octopus.testsupport.DockerisedOctopusDeployServer createOctopusServer() throws IOException {

        final Network network = Network.newNetwork();
        final GenericContainer<?> msSqlContainer =
                new GenericContainer<>(DockerImageName.parse(MS_SQL_IMAGE))
                        .withExposedPorts(1433)
                        .withNetworkAliases(MS_SQL_CONTAINER_NETWORK_ALIAS)
                        .withNetwork(network)
                        .withEnv("SA_PASSWORD", SA_PASSWORD)
                        .withEnv("MSSQL_TCP_PORT", Integer.toString(MS_SQL_PORT))
                        .withEnv("ACCEPT_EULA", "Y")
                        .withEnv("MSSQL_PID", "Developer")
                        .withLogConsumer(outputFrame -> {
                            // Print the output from the container to stdout
                            System.out.println("TestContainerLogSql: " + outputFrame.getUtf8String());
                        })
                        .waitingFor(
                                Wait.forLogMessage(".*SQL Server is now ready for client connections.*", 1));
        msSqlContainer.start();

        final StringBuilder connectionStringBuilder = new StringBuilder();
        connectionStringBuilder
                .append("Server=")
                .append(msSqlContainer.getNetworkAliases().get(0))
                .append(",")
                .append(MS_SQL_PORT)
                .append(";")
                .append("Database=OctopusDeploy;")
                .append("User=sa;")
                .append("Password=")
                .append(SA_PASSWORD);

        final GenericContainer<?> octopusDeployServerContainer =
                new GenericContainer<>(DockerImageName.parse(OCTOPUS_SERVER_IMAGE))
                        .withExposedPorts(OCTOPUS_SERVER_DEPLOY_PORT)
                        .withNetwork(network)
                        .withNetworkAliases("OCTOPUS_SERVER")
                        .withEnv("ACCEPT_EULA", "Y")
                        .withEnv("ADMIN_USERNAME", "admin")
                        .withEnv("ADMIN_PASSWORD", OCTOPUS_DEPLOY_SERVER_PASSWORD)
                        .withEnv("ADMIN_EMAIL", "octopusJavaSdkTest@octopus.com")
                        .withEnv("DB_CONNECTION_STRING", connectionStringBuilder.toString())
                        .withStartupTimeout(Duration.ofMinutes(5))
                        .withLogConsumer(outputFrame -> {
                            // Print the output from the container to stdout
                            System.out.println("TestContainerLogDocker: " + outputFrame.getUtf8String());
                        })
                        .waitingFor(Wait.forLogMessage(".*Web server is ready to process requests.*", 1));

        try {
            octopusDeployServerContainer.start();

            final String octopusServerUrlString = generateOctopusServerUrl(octopusDeployServerContainer);
            System.out.println("Launching Octopus Server on " + octopusServerUrlString);

            final OkHttpClient httpClient =
                    new OkHttpClient.Builder().cookieJar(new InMemoryCookieJar()).build();
            final OctopusClient client = new OctopusClient(httpClient, new URL(octopusServerUrlString));
            client.login(OCTOPUS_SERVER_USERNAME, OCTOPUS_DEPLOY_SERVER_PASSWORD);
            final String apiKey = createApiKeyForCurrentUser(client);
            installLicense(client);

            return new com.octopus.testsupport.DockerisedOctopusDeployServer(
                    msSqlContainer, octopusDeployServerContainer, apiKey);
        } catch (final Exception e) {
            msSqlContainer.stop();
            octopusDeployServerContainer.stop();
            throw e;
        }
    }

    private static String generateOctopusServerUrl(final GenericContainer<?> octopusServerContainer) {
        final String OCTOPUS_SERVER_URL_TEMPLATE = "http://%s:%d";
        return String.format(
                OCTOPUS_SERVER_URL_TEMPLATE,
                octopusServerContainer.getHost(),
                octopusServerContainer.getFirstMappedPort());
    }

    @Before
    public void setUp() throws IOException {
        // Configure Jenkins Octopus Server connection
        final OctopusDeployServer testServer = new OctopusDeployServer("TestServer",
                server.getOctopusUrl(), server.getApiKey(), true);
        final OctopusDeployPlugin.DescriptorImpl configDescriptor
                = jenkinsRule.jenkins.getDescriptorByType(OctopusDeployPlugin.DescriptorImpl.class);
        configDescriptor.setOctopusDeployServers(Collections.singletonList(testServer));
        configDescriptor.save();

        // Configure Jenkins OctoCLI
        final OctoInstallation installation =
                new OctoInstallation("Default", System.getenv("OCTOPUS_CLI_PATH"));
        final OctoInstallation.DescriptorImpl cliDescriptor =
                jenkinsRule.jenkins.getDescriptorByType(OctoInstallation.DescriptorImpl.class);
        cliDescriptor.setInstallations(installation);
        cliDescriptor.save();

        // Get Space and configure Octopus server for tests
        final OctopusClient client =
                new OctopusClient(new OkHttpClient(), new URL(server.getOctopusUrl()), server.getApiKey());
        space = new Repository(client).spaces().getAll().get(0);
    }

    @Test
    public void e2eBuildStepTest() throws Exception {
        final FreeStyleProject project = (FreeStyleProject) jenkinsRule.jenkins
                .createProjectFromXML("E2E Test", new ByteArrayInputStream(getProjectConfiguration().getBytes()));
        project.setCustomWorkspace(temporaryFolder.getRoot().toString());
        project.setScm(new SingleFileSCM("test.txt", "test"));

        jenkinsRule.buildAndAssertSuccess(project);

        // OctopusDeployPackRecorder - ZIP file created on local file system
        assertThat(new File(String.valueOf(temporaryFolder.getRoot().toPath().resolve("PackageId.1.0.0.zip")))).exists().isFile();

        // OctopusDeployPushRecorder - Packaged pushed to Octopus
        assertThat(space.packages().getAll())
                .flatExtracting("title", "version").contains("PackageId", "1.0.0");

        // OctopusDeployPushBuildInformationRecorder - Build info pushed to Octopus
        assertThat(space.buildInformation().getAll().stream().map(BuildInformation::getProperties))
                .flatExtracting("packageId", "version").contains("PackageId", "1.0.0");
    }

    private String getProjectConfiguration() throws URISyntaxException, IOException {
        final String rawConfig = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(getClass()
                        .getClassLoader()
                        .getResource("e2eTestProjectConfig.xml"))
                .toURI())));

        return rawConfig.replace("<outputPath>.</outputPath>",
                "<outputPath>" + temporaryFolder.getRoot() + "</outputPath>");
    }

}

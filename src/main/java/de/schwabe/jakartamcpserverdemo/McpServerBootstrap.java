package de.schwabe.jakartamcpserverdemo;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebListener;
import reactor.core.scheduler.Schedulers;

@WebListener
public class McpServerBootstrap implements ServletContextListener {

    static final String MCP_ENDPOINT = "/mcp";

    @Inject
    PersonService personService;

    private McpSyncServer mcpServer;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        mcpServer = McpServer.sync(transport)
                .serverInfo("person-mcp-server", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(new PersonTools(personService, jsonMapper).specifications())
                .build();

        ServletRegistration.Dynamic registration = event.getServletContext().addServlet("mcpServlet", transport);
        if (registration == null) {
            mcpServer.closeGracefully();
            throw new IllegalStateException("Servlet 'mcpServlet' is already registered");
        }
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addMapping(MCP_ENDPOINT);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        try {
            if (mcpServer != null) {
                mcpServer.closeGracefully();
            }
        } finally {
            Schedulers.shutdownNow();
        }
    }
}

package com.medinasystems.pos.shared.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.NodeRole;

/**
 * Reports which node this installation is. Used to check that a central or
 * store instance is running with the expected profile.
 */
@RestController
public class SystemInfoController {

    private final String application;
    private final String version;
    private final NodeProperties node;

    public SystemInfoController(@Value("${spring.application.name}") String application,
                                ObjectProvider<BuildProperties> buildProperties,
                                NodeProperties node) {
        this.application = application;
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "dev";
        this.node = node;
    }

    @GetMapping("/api/v1/system/info")
    public SystemInfo info() {
        return new SystemInfo(application, version, node.role(), node.number());
    }

    public record SystemInfo(String application, String version, NodeRole nodeRole, int nodeNumber) {
    }
}

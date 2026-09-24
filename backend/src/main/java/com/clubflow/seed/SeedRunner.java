package com.clubflow.seed;

import com.clubflow.user.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs once at startup: makes sure permissions, the first SuperAdmin and (optionally) demo data exist. */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class SeedRunner implements ApplicationRunner {
    private final PermissionService permissions;
    private final SeedService seed;

    @Override
    public void run(ApplicationArguments args) {
        permissions.seedDefaultsIfEmpty();
        seed.createSuperAdminIfMissing();
        seed.createDemoDataIfRequested();
    }
}

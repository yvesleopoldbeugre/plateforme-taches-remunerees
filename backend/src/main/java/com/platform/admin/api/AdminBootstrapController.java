package com.platform.admin.api;

import com.platform.admin.api.dto.BootstrapAdminRequest;
import com.platform.admin.application.AdminBootstrapService;
import com.platform.users.api.dto.UserAdminResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public — see {@code AdminBootstrapService} for why, and {@code SecurityConfig} for the permitAll entry. */
@RestController
public class AdminBootstrapController {

    private final AdminBootstrapService bootstrapService;

    public AdminBootstrapController(AdminBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping("/api/v1/admin/bootstrap")
    public UserAdminResponse bootstrap(@Valid @RequestBody BootstrapAdminRequest request) {
        return UserAdminResponse.of(bootstrapService.bootstrap(request.secret(), request.email()));
    }
}

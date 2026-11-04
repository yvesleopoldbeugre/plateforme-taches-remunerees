package com.platform.admin.api;

import com.platform.admin.api.dto.BootstrapAdminRequest;
import com.platform.admin.application.AdminBootstrapService;
import com.platform.users.api.dto.UserAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public — see {@code AdminBootstrapService} for why, and {@code SecurityConfig} for the permitAll entry. */
@RestController
@Tag(name = "Administration", description = "Amorçage du premier compte administrateur, files de "
        + "supervision transverses (organisations, campagnes) et journal d'audit.")
public class AdminBootstrapController {

    private final AdminBootstrapService bootstrapService;

    public AdminBootstrapController(AdminBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Operation(summary = "Amorcer le premier compte PLATFORM_ADMIN", description = "Public (aucun admin "
            + "n'existe encore) mais protégé par un secret partagé (ADMIN_BOOTSTRAP_SECRET) ; refuse dès "
            + "qu'un PLATFORM_ADMIN existe déjà. La cible doit déjà avoir un compte — cet appel ne fait "
            + "que le promouvoir.")
    @PostMapping("/api/v1/admin/bootstrap")
    public UserAdminResponse bootstrap(@Valid @RequestBody BootstrapAdminRequest request) {
        return UserAdminResponse.of(bootstrapService.bootstrap(request.secret(), request.email()));
    }
}

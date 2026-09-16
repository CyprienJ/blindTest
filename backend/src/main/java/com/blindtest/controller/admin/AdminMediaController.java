package com.blindtest.controller.admin;

import com.blindtest.model.dto.MediaReconciliationReport;
import com.blindtest.service.MediaReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator operations for private media maintenance. */
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaReconciliationService reconciliationService;

    /** Reports missing and orphaned media objects without mutating storage. */
    @GetMapping("/reconciliation")
    public MediaReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }
}

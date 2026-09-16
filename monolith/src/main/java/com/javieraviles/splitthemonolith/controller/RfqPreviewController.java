package com.javieraviles.splitthemonolith.controller;

import javax.validation.Valid;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.dto.RfqPreviewDto;
import com.javieraviles.splitthemonolith.service.RfqPreviewService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RfqPreviewController {

    private final RfqPreviewService service;

    RfqPreviewController(RfqPreviewService service) {
        this.service = service;
    }

    @PostMapping("/rfqs/preview")
    RfqPreviewDto preview(@Valid @RequestBody RfqDto request) {
        return service.preview(request);
    }
}

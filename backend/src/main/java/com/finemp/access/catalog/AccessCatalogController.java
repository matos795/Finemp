package com.finemp.access.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccessCatalogController {
    @GetMapping("/api/auth/access-catalog")
    public AccessCatalog current() { return AccessCatalog.current(); }
}

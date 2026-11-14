package com.finemp.assets;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = AssetController.class)
public class AssetExceptionHandler {

    @ExceptionHandler(AssetConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(AssetConflictException exception) {
        return Map.of(
                "code",
                switch (exception.reason()) {
                    case STALE_VERSION -> "ASSET_STALE_VERSION";
                    case DUPLICATE_TAG -> "ASSET_DUPLICATE_TAG";
                });
    }
}

package com.finemp.accounting.classification.importing;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.finemp.integration.planq.PlanoQUploadValidator;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies/{companyId}/accounting-accounts/classification-import")
public class ClassificationImportController {

    private final ClassificationImportPreviewService previewService;
    private final PlanoQUploadValidator uploadValidator;
    private final ClassificationImportApplyService applyService;

    public ClassificationImportController(
            ClassificationImportPreviewService previewService,
            ClassificationImportApplyService applyService,
            PlanoQUploadValidator uploadValidator) {

        this.previewService = previewService;
        this.applyService = applyService;
        this.uploadValidator = uploadValidator;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClassificationImportPreviewResponse preview(
            @PathVariable UUID companyId,
            @RequestPart("file") MultipartFile file) {

        uploadValidator.validate(file);

        try (InputStream inputStream = file.getInputStream()) {

            var items = previewService.preview(
                    companyId,
                    inputStream);

            return ClassificationImportPreviewResponse
                    .from(items);

        } catch (IOException exception) {

            throw new PlanoQReadException(
                    "Não foi possível ler o arquivo enviado.",
                    exception);
        }
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClassificationImportApplyResponse apply(
            @PathVariable UUID companyId,
            @RequestPart("file") MultipartFile file) {

        uploadValidator.validate(file);

        try (InputStream inputStream = file.getInputStream()) {

            return applyService.apply(
                    companyId,
                    inputStream);

        } catch (IOException exception) {

            throw new PlanoQReadException(
                    "Não foi possível ler o arquivo enviado.",
                    exception);
        }
    }

    @ExceptionHandler(PlanoQReadException.class)
    public ProblemDetail handlePlanoQReadException(
            PlanoQReadException exception) {

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST);

        problem.setTitle(
                "Arquivo PlanoQ inválido");

        problem.setDetail(
                exception.getMessage());

        return problem;
    }
}
package com.conxist.workspaces.adapter.web;

import java.util.List;
import java.util.UUID;
import com.conxist.workspaces.application.ManageVariables;
import com.conxist.workspaces.domain.Variable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The variables of the workspace the request names (ADR 0018). */
@RestController
@RequestMapping("/api/variables")
class VariablesController {

    /** Bounds what is read before the rules in Variable apply; those count characters, this counts UTF-16 units. */
    private static final int MAX_INPUT = 8_000;

    private final ManageVariables variables;

    VariablesController(ManageVariables variables) {
        this.variables = variables;
    }

    @GetMapping
    List<VariableView> list() {
        return variables.list().stream().map(VariableView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VariableView add(@Valid @RequestBody VariableRequest request) {
        return VariableView.of(variables.add(request.name(), request.value()));
    }

    @PutMapping("/{id}")
    VariableView change(@PathVariable UUID id, @Valid @RequestBody VariableRequest request) {
        return VariableView.of(variables.change(id, request.name(), request.value()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID id) {
        variables.remove(id);
    }

    record VariableRequest(@Size(max = MAX_INPUT) String name, @Size(max = MAX_INPUT) String value) {
    }

    /** A variable as the app shows it; the workspace is implied by the request. */
    record VariableView(UUID id, String name, String value) {

        static VariableView of(Variable variable) {
            return new VariableView(variable.id(), variable.name(), variable.value());
        }
    }
}

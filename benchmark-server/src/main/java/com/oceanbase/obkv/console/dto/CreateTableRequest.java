package com.oceanbase.obkv.console.dto;

import com.oceanbase.obkv.console.model.SqlConnection;
import com.oceanbase.obkv.console.model.TableConfig;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Request DTO for creating a table
 */
@Data
public class CreateTableRequest {

    /**
     * SQL connection configuration
     */
    @NotNull(message = "SQL connection is required")
    @Valid
    private SqlConnection sqlConnection;

    /**
     * Table configuration
     */
    @NotNull(message = "Table configuration is required")
    @Valid
    private TableConfig tableConfig;
}


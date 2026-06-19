package com.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;

@Data
public class ShortLinkCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "原始链接不能为空")
    @URL(message = "URL格式不正确")
    private String originalUrl;

    private String description;

    private Integer expireDays;

    @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9-]{0,6}[A-Za-z0-9])?$",
            message = "自定义短码格式不正确，2-8位字母数字或连字符，不能以连字符开头或结尾")
    private String customCode;
}

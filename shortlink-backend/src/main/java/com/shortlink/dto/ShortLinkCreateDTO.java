package com.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;

@Data
// Serializable，可转成字节流，用于序列化
public class ShortLinkCreateDTO implements Serializable {

    //serialVersionUID 用于验证序列化版本的一致性——当把对象序列化保存，之后反序列化时，JVM 会检查这个值是否匹配，不匹配就抛异常，防止数据错乱。
    private static final long serialVersionUID = 1L;

    // 校验字符串不能为 null、不能为空串 ""、不能全是空格
    @NotBlank(message = "原始链接不能为空")
    // 校验是否符合 URL 格式（如 http:// 或 https:// 开头，带域名等）
    @URL(message = "URL格式不正确")
    private String originalUrl;

    private String description;

    private Integer expireDays;

    @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9-]{0,6}[A-Za-z0-9])?$",
            message = "自定义短码格式不正确，1-8位字母数字或连字符，不能以连字符开头或结尾")
    private String customCode;
}

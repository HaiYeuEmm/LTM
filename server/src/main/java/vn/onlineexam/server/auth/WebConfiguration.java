package vn.onlineexam.server.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final TeacherAuthInterceptor teacherAuthInterceptor;
    private final StudentAuthInterceptor studentAuthInterceptor;

    public WebConfiguration(TeacherAuthInterceptor teacherAuthInterceptor, StudentAuthInterceptor studentAuthInterceptor) {
        this.teacherAuthInterceptor = teacherAuthInterceptor;
        this.studentAuthInterceptor = studentAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(teacherAuthInterceptor).addPathPatterns("/api/teacher/**");
        registry.addInterceptor(studentAuthInterceptor).addPathPatterns("/api/student/**");
    }
}

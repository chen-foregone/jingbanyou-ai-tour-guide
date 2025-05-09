package cn.edu.gdou.jingbanyou.framework.config;

import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cn.edu.gdou.jingbanyou.common.constant.Constants;
import cn.edu.gdou.jingbanyou.common.filter.RefererFilter;
import cn.edu.gdou.jingbanyou.common.filter.RepeatableFilter;
import cn.edu.gdou.jingbanyou.common.filter.XssFilter;
import cn.edu.gdou.jingbanyou.framework.filter.TouristRateLimitFilter;
import cn.edu.gdou.jingbanyou.framework.filter.TouristSessionFilter;
import cn.edu.gdou.jingbanyou.common.utils.StringUtils;

/**
 * Filter配置
 *
 * @author ruoyi
 */
@Configuration
public class FilterConfig
{
    @Value("${xss.excludes}")
    private String excludes;

    @Value("${xss.urlPatterns}")
    private String urlPatterns;

    @Value("${referer.allowed-domains}")
    private String allowedDomains;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    @ConditionalOnProperty(value = "xss.enabled", havingValue = "true")
    public FilterRegistrationBean xssFilterRegistration()
    {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setFilter(new XssFilter());
        registration.addUrlPatterns(StringUtils.split(urlPatterns, ","));
        registration.setName("xssFilter");
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        Map<String, String> initParameters = new HashMap<String, String>();
        initParameters.put("excludes", excludes);
        registration.setInitParameters(initParameters);
        return registration;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    @ConditionalOnProperty(value = "referer.enabled", havingValue = "true")
    public FilterRegistrationBean refererFilterRegistration()
    {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setFilter(new RefererFilter());
        registration.addUrlPatterns(Constants.RESOURCE_PREFIX + "/*");
        registration.setName("refererFilter");
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        Map<String, String> initParameters = new HashMap<String, String>();
        initParameters.put("allowedDomains", allowedDomains);
        registration.setInitParameters(initParameters);
        return registration;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    public FilterRegistrationBean someFilterRegistration()
    {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(new RepeatableFilter());
        registration.addUrlPatterns("/*");
        registration.setName("repeatableFilter");
        registration.setOrder(FilterRegistrationBean.LOWEST_PRECEDENCE);
        return registration;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    @ConditionalOnProperty(value = "tourist.rate-limit.enabled", havingValue = "true")
    public FilterRegistrationBean touristRateLimitFilterRegistration(TouristRateLimitFilter touristRateLimitFilter)
    {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(touristRateLimitFilter);
        registration.addUrlPatterns("/tourist/*");
        registration.setName("touristRateLimitFilter");
        // 运行在安全过滤器链之后
        registration.setOrder(FilterRegistrationBean.LOWEST_PRECEDENCE - 10);
        return registration;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    public FilterRegistrationBean touristSessionFilterRegistration(TouristSessionFilter touristSessionFilter)
    {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(touristSessionFilter);
        registration.addUrlPatterns("/tourist/*");
        registration.setName("touristSessionFilter");
        // 运行在限流过滤器之后
        registration.setOrder(FilterRegistrationBean.LOWEST_PRECEDENCE - 9);
        return registration;
    }

}

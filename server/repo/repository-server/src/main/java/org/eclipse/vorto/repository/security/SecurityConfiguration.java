package org.eclipse.vorto.repository.security;

import javax.servlet.Filter;

import org.eclipse.vorto.repository.security.eidp.EidpOAuth2RestTemplate;
import org.eclipse.vorto.repository.security.eidp.EidpResourceDetails;
import org.eclipse.vorto.repository.security.eidp.IsEidpUserRegisteredInterceptor;
import org.eclipse.vorto.repository.security.eidp.JwtTokenUserInfoServices;
import org.eclipse.vorto.repository.web.AngularCsrfHeaderFilter;
import org.eclipse.vorto.repository.web.listeners.RESTAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.filter.OAuth2ClientAuthenticationProcessingFilter;
import org.springframework.security.oauth2.client.filter.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.client.token.AccessTokenProvider;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
@Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
@EnableOAuth2Client
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private RESTAuthenticationEntryPoint authenticationEntryPoint;

	@Autowired
	private PasswordEncoder passwordEncoder;
	
	@Autowired
	private OAuth2ClientContext oauth2ClientContext;
	
	@Autowired
	private EidpResourceDetails eidp;
	
	@Autowired
	private AccessTokenProvider accessTokenProvider;
	
	@Autowired
	private IsEidpUserRegisteredInterceptor eidpInterceptor;

	@Override
	protected void configure(HttpSecurity http) throws Exception {

		http.httpBasic()
			.and()
				.authorizeRequests()
				.antMatchers(HttpMethod.GET, "/rest/**").permitAll()
				.antMatchers("/user/**").permitAll()
				.antMatchers(HttpMethod.PUT, "/rest/**").permitAll()
				.antMatchers(HttpMethod.POST, "/rest/secure/**").authenticated()
				.antMatchers(HttpMethod.DELETE, "/rest/**").authenticated()
			.and()
				.addFilterAfter(new AngularCsrfHeaderFilter(), CsrfFilter.class)
				.addFilterBefore(ssoFilter(), BasicAuthenticationFilter.class)
				.csrf()
					.csrfTokenRepository(csrfTokenRepository())
			.and()
				.csrf()
					.disable()
				.logout()
					.logoutUrl("/logout")
					.logoutSuccessUrl("/")
			.and()
				.headers()
					.frameOptions()
					.sameOrigin()
					.httpStrictTransportSecurity()
					.disable();

		http.exceptionHandling().authenticationEntryPoint(authenticationEntryPoint);
	}
	
	private CsrfTokenRepository csrfTokenRepository() {
		HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
		repository.setHeaderName("X-XSRF-TOKEN");
		return repository;
	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
	}
	
	@Bean
	public FilterRegistrationBean oauth2ClientFilterRegistration(
	    OAuth2ClientContextFilter filter) {
	  FilterRegistrationBean registration = new FilterRegistrationBean();
	  registration.setFilter(filter);
	  registration.setOrder(-100);
	  return registration;
	}
	
	private Filter ssoFilter() {
		OAuth2ClientAuthenticationProcessingFilter eidpFilter = new OAuth2ClientAuthenticationProcessingFilter("/eidp/login");
		OAuth2RestTemplate restTemplate = new EidpOAuth2RestTemplate(eidp, oauth2ClientContext);
		eidpFilter.setRestTemplate(restTemplate);
		
		UserInfoTokenServices tokenServices = new JwtTokenUserInfoServices(null, eidp.getClientId(), eidpInterceptor);
		tokenServices.setRestTemplate(restTemplate);
		eidpFilter.setTokenServices(tokenServices);
		
		restTemplate.setAccessTokenProvider(accessTokenProvider);
		
		return eidpFilter;
	}
	
	@Bean
	@ConfigurationProperties("eidp.oauth2.client")
	public EidpResourceDetails eidp() {
		return new EidpResourceDetails();
	}
}
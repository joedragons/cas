---
layout: default
title: CAS - pac4j Authentication
category: Authentication
---
{% include variables.html %}


<p align="center">
  <img src="https://pac4j.github.io/pac4j/img/logo-cas.png" width="300" />
</p>

## Overview

The [pac4j](https://github.com/pac4j/pac4j) project is a security engine with specific authentication mechanisms, 
called authenticators, for MongoDB, LDAP, JWT, RDBMS...

A pac4j authenticator (and profile creator) can be wrapped 
in a CAS authentication handler and used for authentication.

## Dependency

Support is added by including the following dependency in the WAR overlay:

{% include_cached casmodule.html group="org.apereo.cas" module="cas-server-support-pac4j-authentication" %}

## Configuration

You can use the implementation for CAS username/password credentials: 
`UsernamePasswordWrapperAuthenticationHandler`.

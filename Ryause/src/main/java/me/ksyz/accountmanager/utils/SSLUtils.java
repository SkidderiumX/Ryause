package me.ksyz.accountmanager.utils;

import javax.net.ssl.SSLContext;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SSLUtils {
  private static final SSLContext ctx;

  public static SSLContext getSSLContext() {
    return ctx;
  }

  static {
    try {
      ctx = SSLContext.getDefault();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize default SSLContext", e);
    }
  }
}

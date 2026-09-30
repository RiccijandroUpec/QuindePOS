@echo off
REM    uniCenta oPOS Touch Friendly Point of Sales designed for Touch Screen
REM    Copyright (c) 2009-20143 uniCenta
REM    http://sourceforge.net/projects/unicentaopos
REM
REM    This file is part of uniCenta oPOS
REM
REM    uniCenta oPOS is free software: you can redistribute it and/or modify
REM    it under the terms of the GNU General Public License as published by
REM    the Free Software Foundation, either version 3 of the License, or
REM    (at your option) any later version.
REM
REM    uniCenta oPOS is distributed in the hope that it will be useful,
REM    but WITHOUT ANY WARRANTY; without even the implied warranty of
REM    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM    GNU General Public License for more details.
REM
REM    You should have received a copy of the GNU General Public License
REM    along with uniCenta oPOS.  If not, see http://www.gnu.org/licenses/>
REM
set DIRNAME=%~dp0
REM A packaged distribution places ecopos.jar next to this script; running
REM straight from a source checkout after "ant -f build_working.xml jar"
REM leaves it under build\jar\ instead, so fall back to that.
if exist "%DIRNAME%ecopos.jar" (
    set CP="%DIRNAME%ecopos.jar"
) else (
    set CP="%DIRNAME%build/jar/ecopos.jar"
)
set CP=%CP%;"%DIRNAME%lib/jasperreports-4.5.1.jar"
set CP=%CP%;"%DIRNAME%lib/jcommon-1.0.15.jar"
set CP=%CP%;"%DIRNAME%lib/jfreechart-1.0.12.jar"
set CP=%CP%;"%DIRNAME%lib/jdt-compiler-3.1.1.jar"
set CP=%CP%;"%DIRNAME%lib/commons-beanutils-1.8.3.jar"
set CP=%CP%;"%DIRNAME%lib/commons-digester-2.1.jar"
set CP=%CP%;"%DIRNAME%lib/iText-2.1.7.jar"
set CP=%CP%;"%DIRNAME%lib/poi-3.8-20120326.jar"
set CP=%CP%;"%DIRNAME%lib/barcode4j-2.0.jar"
set CP=%CP%;"%DIRNAME%lib/commons-codec-1.4.jar"
set CP=%CP%;"%DIRNAME%lib/velocity-1.7-dep.jar"
set CP=%CP%;"%DIRNAME%lib/oro-2.0.8.jar"
set CP=%CP%;"%DIRNAME%lib/commons-collections-3.2.2.jar"
set CP=%CP%;"%DIRNAME%lib/commons-lang-2.4.jar"
set CP=%CP%;"%DIRNAME%lib/bsh-core-2.0b4.jar"
set CP=%CP%;"%DIRNAME%lib/RXTXcomm.jar"
set CP=%CP%;"%DIRNAME%lib/jpos1121.jar"
set CP=%CP%;"%DIRNAME%lib/swingx-all-1.6.4.jar"
set CP=%CP%;"%DIRNAME%lib/substance.jar"
set CP=%CP%;"%DIRNAME%lib/flatlaf-3.5.4.jar"
set CP=%CP%;"%DIRNAME%lib/flatlaf-extras-3.5.4.jar"
set CP=%CP%;"%DIRNAME%lib/jsvg-1.4.0.jar"
set CP=%CP%;"%DIRNAME%lib/substance-swingx.jar"
set CP=%CP%;"%DIRNAME%lib/substance-extras.jar"
REM Needed for NetBeans-generated forms (org.jdesktop.layout.GroupLayout) and
REM the Substance L&F animations; missing these causes a NoClassDefFoundError
REM on startup.
set CP=%CP%;"%DIRNAME%lib/swing-layout-1.0.4.jar"
set CP=%CP%;"%DIRNAME%lib/AbsoluteLayout.jar"
set CP=%CP%;"%DIRNAME%lib/trident.jar"

REM Apache Axis SOAP libraries.
set CP=%CP%;"%DIRNAME%lib/axis.jar"
set CP=%CP%;"%DIRNAME%lib/jaxrpc.jar"
set CP=%CP%;"%DIRNAME%lib/saaj.jar"
set CP=%CP%;"%DIRNAME%lib/wsdl4j-1.5.1.jar"
set CP=%CP%;"%DIRNAME%lib/commons-discovery-0.4.jar"
set CP=%CP%;"%DIRNAME%lib/commons-logging-1.1.jar"
set CP=%CP%;"%DIRNAME%locales/"
set CP=%CP%;"%DIRNAME%reports/"
REM ResourceBundle only checks the classpath root, not subfolders, so every
REM bundled language's *_xx.properties files (under locales/<Language>/) must be
REM added explicitly or the app silently falls back to English regardless of
REM the configured user.language.
set CP=%CP%;"%DIRNAME%locales/Albanian/locales/";"%DIRNAME%locales/Albanian/reports/"
set CP=%CP%;"%DIRNAME%locales/American/locales/";"%DIRNAME%locales/American/reports/"
set CP=%CP%;"%DIRNAME%locales/Arabic/locales/";"%DIRNAME%locales/Arabic/reports/"
set CP=%CP%;"%DIRNAME%locales/Argentinian/locales/";"%DIRNAME%locales/Argentinian/reports/"
set CP=%CP%;"%DIRNAME%locales/Brazilian/locales/"
set CP=%CP%;"%DIRNAME%locales/Croatian/locales/";"%DIRNAME%locales/Croatian/reports/"
set CP=%CP%;"%DIRNAME%locales/Dutch/locales/";"%DIRNAME%locales/Dutch/reports/"
set CP=%CP%;"%DIRNAME%locales/English/locales/";"%DIRNAME%locales/English/reports/"
set CP=%CP%;"%DIRNAME%locales/Estonian/locales/";"%DIRNAME%locales/Estonian/reports/"
set CP=%CP%;"%DIRNAME%locales/French/locales/";"%DIRNAME%locales/French/reports/"
set CP=%CP%;"%DIRNAME%locales/German/locales/";"%DIRNAME%locales/German/reports/"
set CP=%CP%;"%DIRNAME%locales/Italian/locales/";"%DIRNAME%locales/Italian/reports/"
set CP=%CP%;"%DIRNAME%locales/Mexican/locales/";"%DIRNAME%locales/Mexican/reports/"
set CP=%CP%;"%DIRNAME%locales/Portuguese/locales/"
set CP=%CP%;"%DIRNAME%locales/Spanish/locales/";"%DIRNAME%locales/Spanish/reports/"

start javaw -cp %CP% -Djava.library.path="%DIRNAME%lib/Windows/i368-mingw32" -Ddirname.path="%DIRNAME%./" -splash:unicenta_splash_dark.png com.openbravo.pos.forms.StartPOS %1

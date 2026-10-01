; Instalador de Quinde POS para Windows (Inno Setup 6.7+).
;
; Se arma con:  ant -f build_working.xml instalador
; (prepara build/instalador/{app,runtime,mariadb} y llama a ISCC con /DVersion y /DOrigen).
;
; Instala Quinde POS con su propio Java (no hace falta instalar Java) y deja elegir la base de datos:
;   - la incluida: MariaDB como servicio de Windows "QuindePOS-DB" (puerto 3310, solo esta computadora),
;     con los datos en C:\ProgramData\QuindePOS\datos y una clave de root aleatoria;
;   - o un servidor MySQL/MariaDB que ya exista.
; Al final ConfigurarBase crea la base y escribe C:\ProgramData\QuindePOS\config\quinde.properties.
; Al abrir Quinde POS por primera vez se crean las tablas y arranca el asistente de configuracion.
;
; Actualizar = instalar encima: se conservan la configuracion y los datos.
; Desinstalar quita el programa y el servicio, pero NO borra los datos ni la configuracion.

#ifndef Version
  #define Version "1.0.0"
#endif
#ifndef Origen
  #define Origen "..\..\build\instalador"
#endif
#define Raiz "..\.."
#define Servicio "QuindePOS-DB"
#define PuertoIncluido "3310"
#define ArchivoConfig "{commonappdata}\QuindePOS\config\quinde.properties"

[Setup]
AppId={{00CD29F3-9D83-4E5B-94E3-99E9D14F5CFB}
AppName=Quinde POS
AppVersion={#Version}
AppVerName=Quinde POS {#Version}
AppPublisher=Quinde POS
AppPublisherURL=https://quindepos.cyrshop.app
AppSupportURL=https://github.com/RiccijandroUpec/QuindePOS/issues
AppUpdatesURL=https://github.com/RiccijandroUpec/QuindePOS/releases
AppCopyright=Copyright (c) 2009-2014 uniCenta, 2026 Quinde POS - GPL v3
VersionInfoVersion={#Version}
VersionInfoDescription=Instalador de Quinde POS
DefaultDirName={autopf}\Quinde POS
DefaultGroupName=Quinde POS
DisableProgramGroupPage=yes
LicenseFile={#Raiz}\LICENSE
OutputBaseFilename=QuindePOS-Setup-{#Version}
SetupIconFile={#Raiz}\quinde.ico
UninstallDisplayIcon={app}\quinde.ico
UninstallDisplayName=Quinde POS
WizardStyle=modern
WizardImageFile=imagenes\lateral-0.png,imagenes\lateral-1.png,imagenes\lateral-2.png,imagenes\lateral-3.png,imagenes\lateral-4.png,imagenes\lateral-5.png,imagenes\lateral-6.png
WizardSmallImageFile=imagenes\icono-0.png,imagenes\icono-1.png,imagenes\icono-2.png,imagenes\icono-3.png,imagenes\icono-4.png,imagenes\icono-5.png,imagenes\icono-6.png
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
MinVersion=10.0
CloseApplications=yes
RestartApplications=no
SetupLogging=yes

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Messages]
es.WelcomeLabel2=Se instalará [name/ver] en tu computadora.%n%nIncluye todo lo necesario: no hace falta instalar Java ni una base de datos aparte.%n%nAl terminar, un asistente te ayuda a dejar tu caja lista para vender.
es.FinishedLabel=Quinde POS quedó instalado.%n%nLa primera vez que lo abras se prepara la base de datos y arranca el asistente de configuración.%n%nUsuario inicial: Administrador (sin clave; el asistente te pide ponerle una).

[Tasks]
Name: "escritorio"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"

[Dirs]
; Quinde POS escribe aqui la configuracion de facturacion electronica, la firma y los registros.
Name: "{app}\sri-conector"; Permissions: users-modify
Name: "{commonappdata}\QuindePOS\config"; Permissions: users-modify; Flags: uninsneveruninstall

[Files]
Source: "{#Origen}\app\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Origen}\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
; Va siempre: aunque se use otro servidor, las copias de seguridad usan mariadb-dump.
Source: "{#Origen}\mariadb\*"; DestDir: "{app}\mariadb"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Quinde POS"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "{code:ParametrosLanzar}"; WorkingDir: "{app}"; IconFilename: "{app}\quinde.ico"; Comment: "Punto de venta"
Name: "{autodesktop}\Quinde POS"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "{code:ParametrosLanzar}"; WorkingDir: "{app}"; IconFilename: "{app}\quinde.ico"; Comment: "Punto de venta"; Tasks: escritorio

[Run]
Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "{code:ParametrosLanzar}"; WorkingDir: "{app}"; Description: "Abrir Quinde POS"; Flags: postinstall nowait skipifsilent

[UninstallRun]
; "net stop" espera a que el servicio pare, asi se pueden borrar los archivos de MariaDB.
Filename: "{sys}\net.exe"; Parameters: "stop {#Servicio}"; Flags: runhidden; RunOnceId: "DetenerBase"
Filename: "{sys}\sc.exe"; Parameters: "delete {#Servicio}"; Flags: runhidden; RunOnceId: "QuitarServicio"

[Code]
var
  PaginaBase: TInputOptionWizardPage;
  PaginaServidor: TInputQueryWizardPage;
  ConfiguradoAntes: Boolean;

function ArchivoConfig: String;
begin
  Result := ExpandConstant('{#ArchivoConfig}');
end;

// -Ddirname.path termina en dos barras antes de la comilla: con una sola, Windows tomaria \" como comilla literal.
function ParametrosLanzar(Param: String): String;
begin
  Result := '-splash:quinde_splash.png "-Ddirname.path=' + ExpandConstant('{app}') + '\\" ' +
            '-Djava.library.path=lib\Windows\i368-mingw32 -jar ecopos.jar "' + ArchivoConfig + '"';
end;

function CarpetaDatos: String;
begin
  Result := ExpandConstant('{commonappdata}\QuindePOS\datos');
end;

function UsarIncluida: Boolean;
begin
  Result := PaginaBase.SelectedValueIndex = 0;
end;

function Ejecutar(const Programa, Parametros: String): Integer;
var
  Codigo: Integer;
begin
  if not Exec(Programa, Parametros, ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, Codigo) then
    Codigo := -1;
  Log('Ejecutar ' + ExtractFileName(Programa) + ' -> ' + IntToStr(Codigo));
  Result := Codigo;
end;

function ServicioExiste: Boolean;
begin
  Result := Ejecutar(ExpandConstant('{sys}\sc.exe'), 'query {#Servicio}') = 0;
end;

procedure InitializeWizard;
begin
  ConfiguradoAntes := FileExists(ArchivoConfig);

  PaginaBase := CreateInputOptionPage(wpSelectDir,
    'Base de datos', '¿Dónde guarda Quinde POS tus ventas, productos y clientes?',
    'Si es tu primera caja o no sabes qué elegir, deja la opción recomendada.',
    True, False);
  PaginaBase.Add('Instalar la base de datos incluida (recomendado): todo queda en esta computadora');
  PaginaBase.Add('Conectarme a un servidor MySQL o MariaDB que ya tengo');
  PaginaBase.SelectedValueIndex := 0;

  PaginaServidor := CreateInputQueryPage(PaginaBase.ID,
    'Servidor de base de datos', 'Datos de tu servidor MySQL o MariaDB',
    'Quinde POS crea la base si no existe; el usuario necesita permiso para crear bases de datos. ' +
    'Si algo no conecta, al abrir Quinde POS podrás corregirlo en la pantalla de configuración.');
  PaginaServidor.Add('Servidor:', False);
  PaginaServidor.Add('Puerto:', False);
  PaginaServidor.Add('Usuario:', False);
  PaginaServidor.Add('Clave:', True);
  PaginaServidor.Add('Nombre de la base:', False);
  PaginaServidor.Values[0] := 'localhost';
  PaginaServidor.Values[1] := '3306';
  PaginaServidor.Values[2] := 'root';
  PaginaServidor.Values[4] := 'quindepos';
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  // Actualizacion: ya hay configuracion, no se pregunta nada de la base.
  if ConfiguradoAntes and ((PageID = PaginaBase.ID) or (PageID = PaginaServidor.ID)) then
    Result := True
  else if (PageID = PaginaServidor.ID) and UsarIncluida then
    Result := True;
end;

function SoloLetrasNumeros(const S: String): Boolean;
var
  I: Integer;
begin
  Result := Length(S) > 0;
  for I := 1 to Length(S) do
    if not (((S[I] >= 'a') and (S[I] <= 'z')) or ((S[I] >= 'A') and (S[I] <= 'Z')) or
            ((S[I] >= '0') and (S[I] <= '9')) or (S[I] = '_')) then
      Result := False;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID = PaginaServidor.ID then
  begin
    if (Trim(PaginaServidor.Values[0]) = '') or (Trim(PaginaServidor.Values[2]) = '') then
    begin
      MsgBox('Escribe el servidor y el usuario.', mbError, MB_OK);
      Result := False;
    end
    else if StrToIntDef(Trim(PaginaServidor.Values[1]), 0) <= 0 then
    begin
      MsgBox('El puerto debe ser un número (normalmente 3306).', mbError, MB_OK);
      Result := False;
    end
    else if not SoloLetrasNumeros(Trim(PaginaServidor.Values[4])) then
    begin
      MsgBox('El nombre de la base solo puede tener letras, números y _.', mbError, MB_OK);
      Result := False;
    end;
  end;
end;

function UpdateReadyMemo(Space, NewLine, MemoUserInfoInfo, MemoDirInfo, MemoTypeInfo,
  MemoComponentsInfo, MemoGroupInfo, MemoTasksInfo: String): String;
begin
  Result := MemoDirInfo + NewLine + NewLine + 'Base de datos:' + NewLine;
  if ConfiguradoAntes then
    Result := Result + Space + 'Se conserva la configuración actual (' + ArchivoConfig + ')'
  else if UsarIncluida then
    Result := Result + Space + 'MariaDB incluido, como servicio de Windows ({#Servicio}, puerto {#PuertoIncluido})' + NewLine +
              Space + 'Datos en ' + CarpetaDatos
  else
    Result := Result + Space + PaginaServidor.Values[2] + '@' + PaginaServidor.Values[0] + ':' +
              PaginaServidor.Values[1] + ', base ' + PaginaServidor.Values[4];
  if MemoTasksInfo <> '' then
    Result := Result + NewLine + NewLine + MemoTasksInfo;
end;

// Actualizacion: el servicio usa los archivos de {app}\mariadb, hay que pararlo antes de copiar.
function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if ServicioExiste then
    Ejecutar(ExpandConstant('{sys}\net.exe'), 'stop {#Servicio}');
end;

function Comillas(const S: String): String;
begin
  Result := '"' + S + '"';
end;

// Corre ConfigurarBase con el Java incluido. Devuelve '' si salio bien, o el motivo del error.
function ConfigurarBase(const Parametros: String): String;
var
  Resultado: String;
  Texto: AnsiString;
begin
  Resultado := ExpandConstant('{tmp}\resultado-base.txt');
  DeleteFile(Resultado);
  if Ejecutar(ExpandConstant('{app}\runtime\bin\java.exe'),
       '-cp ' + Comillas(ExpandConstant('{app}\ecopos.jar')) + ' com.openbravo.pos.instalacion.ConfigurarBase' +
       ' --config ' + Comillas(ArchivoConfig) + ' --dir ' + Comillas(ExpandConstant('{app}')) +
       ' --mysqldump ' + Comillas(ExpandConstant('{app}\mariadb\bin\mariadb-dump.exe')) +
       ' --resultado ' + Comillas(Resultado) + ' ' + Parametros) = 0 then
    Result := ''
  else
  begin
    Result := 'No se pudo preparar la base de datos.';
    if LoadStringFromFile(Resultado, Texto) then
      Result := UTF8Decode(Texto);
  end;
end;

procedure PrepararBaseIncluida;
var
  Datos, Bin, Error, ArchivoClave: String;
begin
  Datos := CarpetaDatos;
  Bin := ExpandConstant('{app}\mariadb\bin\');
  if ServicioExiste then
  begin
    Ejecutar(ExpandConstant('{sys}\net.exe'), 'start {#Servicio}');
    Exit;
  end;
  if FileExists(Datos + '\my.ini') then
  begin
    // Reinstalacion: los datos quedaron de antes, solo falta volver a registrar el servicio.
    Ejecutar(Bin + 'mariadbd.exe', '--install {#Servicio} --defaults-file=' + Comillas(Datos + '\my.ini'));
    Ejecutar(ExpandConstant('{sys}\net.exe'), 'start {#Servicio}');
    Exit;
  end;
  if ConfiguradoAntes then
    Exit; // usa otro servidor

  WizardForm.StatusLabel.Caption := 'Creando la base de datos (MariaDB)...';
  ForceDirectories(ExtractFileDir(Datos));
  if Ejecutar(Bin + 'mariadb-install-db.exe',
       '--datadir=' + Comillas(Datos) + ' --service={#Servicio} --port={#PuertoIncluido}') <> 0 then
  begin
    MsgBox('No se pudo crear la base de datos incluida (MariaDB).' + #13#10 +
           'Al abrir Quinde POS podrás configurar otra base de datos.', mbError, MB_OK);
    Exit;
  end;
  // Solo esta computadora, y nombres de tablas como los espera Quinde POS.
  SaveStringToFile(Datos + '\my.ini', #13#10 + '[mysqld]' + #13#10 +
    'bind-address=127.0.0.1' + #13#10 +
    'character-set-server=utf8' + #13#10 +
    'collation-server=utf8_general_ci' + #13#10 +
    'innodb_buffer_pool_size=256M' + #13#10 +
    'max_allowed_packet=64M' + #13#10, True);
  Ejecutar(ExpandConstant('{sys}\sc.exe'), 'config {#Servicio} start= auto');
  Ejecutar(ExpandConstant('{sys}\sc.exe'), 'description {#Servicio} "Base de datos de Quinde POS (MariaDB)"');
  Ejecutar(ExpandConstant('{sys}\net.exe'), 'start {#Servicio}');

  WizardForm.StatusLabel.Caption := 'Configurando Quinde POS...';
  // root recien creado sin clave (solo desde esta computadora): ConfigurarBase le pone una aleatoria.
  ArchivoClave := ExpandConstant('{tmp}\clave-base.txt');
  SaveStringToFile(ArchivoClave, '', False);
  Error := ConfigurarBase('--host 127.0.0.1 --puerto {#PuertoIncluido} --usuario root --clave-archivo ' +
    Comillas(ArchivoClave) + ' --base quindepos --esperar 90 --generar-clave');
  if Error <> '' then
    MsgBox(Error, mbError, MB_OK);
end;

procedure PrepararServidorExistente;
var
  Error, ArchivoClave: String;
begin
  WizardForm.StatusLabel.Caption := 'Conectando con tu servidor de base de datos...';
  // La clave va en un archivo temporal (se borra al terminar), no en la linea de comandos.
  ArchivoClave := ExpandConstant('{tmp}\clave-base.txt');
  SaveStringToFile(ArchivoClave, PaginaServidor.Values[3], False);
  Error := ConfigurarBase('--host ' + Comillas(Trim(PaginaServidor.Values[0])) +
    ' --puerto ' + Trim(PaginaServidor.Values[1]) +
    ' --usuario ' + Comillas(Trim(PaginaServidor.Values[2])) +
    ' --clave-archivo ' + Comillas(ArchivoClave) +
    ' --base ' + Trim(PaginaServidor.Values[4]) + ' --esperar 5 --guardar-aunque-falle');
  DeleteFile(ArchivoClave);
  if Error <> '' then
    MsgBox(Error, mbInformation, MB_OK);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    if ConfiguradoAntes or UsarIncluida then
      PrepararBaseIncluida
    else
      PrepararServidorExistente;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if (CurUninstallStep = usPostUninstall) and not UninstallSilent then
    MsgBox('Se quitó Quinde POS. Tus datos no se borraron:' + #13#10 +
           ExpandConstant('{commonappdata}\QuindePOS') + #13#10 + #13#10 +
           'Si vuelves a instalar Quinde POS, los encontrará y seguirá donde quedaste.', mbInformation, MB_OK);
end;

package com.damien.mesfichiers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun FormScreen(screen: Screen.Form) {
    val cs = MaterialTheme.colorScheme
    val existing = remember { screen.serverId?.let { id -> AppModel.servers.firstOrNull { it.id == id } } }
    val scope = rememberCoroutineScope()

    var host by rememberSaveable { mutableStateOf(existing?.cfg?.host ?: "") }
    var port by rememberSaveable { mutableStateOf((existing?.cfg?.port ?: 21).toString()) }
    var sec by rememberSaveable { mutableStateOf(existing?.cfg?.security?.ordinal ?: 0) }
    var user by rememberSaveable { mutableStateOf(existing?.cfg?.user ?: "") }
    var pass by rememberSaveable { mutableStateOf(existing?.cfg?.password ?: "") }
    var anon by rememberSaveable { mutableStateOf(existing?.cfg?.anonymous ?: false) }
    var passive by rememberSaveable { mutableStateOf(if (existing != null) existing.cfg.passive else true) }
    var enc by rememberSaveable { mutableStateOf(if (existing?.cfg?.encoding == "ISO-8859-1") 1 else 0) }
    var trust by rememberSaveable { mutableStateOf(existing?.cfg?.trustAll ?: false) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf<String?>(null) }

    fun build(): FtpConfig? {
        val p = port.trim().toIntOrNull()
        if (host.isBlank() || p == null || p < 1 || p > 65535) return null
        return FtpConfig(
            host = host.trim(),
            port = p,
            security = Security.entries[sec],
            user = user.trim(),
            password = pass,
            anonymous = anon,
            passive = passive,
            encoding = if (enc == 1) "ISO-8859-1" else "UTF-8",
            trustAll = trust
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { AppModel.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    if (existing == null) "Nouveau serveur FTP" else "Détails concernant " + existing.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                Field("Adresse", host, { host = it }, keyboard = KeyboardType.Uri)
                Field("Port", port, { port = it }, keyboard = KeyboardType.Number)
                Choice("Sécurité", Security.entries.map { it.label }, sec) { sec = it }
                if (sec != 0) {
                    CheckRow("Accepter un certificat auto-signé", trust) { trust = it }
                }
                Field("Nom d'utilisateur", user, { user = it }, enabled = !anon)
                Field(
                    "Mot de passe", pass, { pass = it }, enabled = !anon,
                    keyboard = KeyboardType.Password, hide = !showPass,
                    trailing = {
                        TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Masquer" else "Afficher") }
                    }
                )
                CheckRow("Se connecter anonymement", anon) { anon = it }
                Choice("Mode de transfert", listOf("Passif", "Actif"), if (passive) 0 else 1) { passive = it == 0 }
                Field("Pseudo", name, { name = it })
                Choice("Codage", listOf("UTF-8", "ISO-8859-1"), enc) { enc = it }

                Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
                    Button(
                        enabled = !testing,
                        onClick = {
                            val cfg = build()
                            if (cfg == null) {
                                testMsg = "Renseigne l'adresse et un port valide"
                            } else {
                                scope.launch {
                                    testing = true
                                    testMsg = null
                                    testMsg = try {
                                        withContext(Dispatchers.IO) {
                                            val c = FtpOps.open(cfg)
                                            try {
                                                val n = FtpOps.list(c, FtpOps.resolve(c, "~")).size
                                                "✓ Connexion réussie ($n éléments dans ton dossier)"
                                            } finally {
                                                FtpOps.quietClose(c)
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        "✗ " + AppModel.friendly(e)
                                    }
                                    testing = false
                                }
                            }
                        }
                    ) { Text(if (testing) "Test en cours…" else "Tester la connexion") }
                    val msg = testMsg
                    if (msg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(msg, color = if (msg.startsWith("✓")) cs.primary else cs.error, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(110.dp))
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            shape = CircleShape,
            color = cs.surface,
            shadowElevation = 10.dp
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { AppModel.back() }) { Text("Annuler", fontSize = 18.sp, color = cs.onSurface) }
                Box(Modifier.width(1.dp).height(26.dp).background(cs.outlineVariant))
                TextButton(
                    onClick = {
                        val cfg = build()
                        if (cfg == null) {
                            testMsg = "Renseigne l'adresse et un port valide"
                        } else {
                            val id = existing?.id ?: UUID.randomUUID().toString()
                            AppModel.saveServer(Server(id, name.trim().ifBlank { cfg.host }, cfg))
                            AppModel.back()
                        }
                    }
                ) { Text("Enregistrer", fontSize = 18.sp, color = cs.onSurface) }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    hide: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = trailing,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

@Composable
private fun Choice(label: String, options: List<String>, index: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Box {
            Text(
                options[index.coerceIn(0, options.lastIndex)] + "   ▾",
                fontSize = 18.sp,
                modifier = Modifier.clickable { open = true }.padding(vertical = 8.dp)
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = {
                        onPick(i)
                        open = false
                    })
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onChange(it) })
        Text(label, fontSize = 17.sp)
    }
}

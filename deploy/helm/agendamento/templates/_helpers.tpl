{{- define "agendamento.nome" -}}{{ .Release.Name }}-agendamento{{- end -}}
{{- define "agendamento.rotulos" -}}
app.kubernetes.io/name: agendamento
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}
{{- define "agendamento.seletor" -}}
app.kubernetes.io/name: agendamento
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

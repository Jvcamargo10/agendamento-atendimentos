/** Regras compartilhadas pelas telas e pela demonstração. Espelham o back-end em Java. */

export const FUSO = 'America/Sao_Paulo';

export function digitosCpf(cpf: string): string {
  return cpf.replace(/\D/g, '');
}

export function cpfValido(cpf: string): boolean {
  const d = digitosCpf(cpf);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const dv = (n: number) => {
    let soma = 0;
    for (let i = 0; i < n; i++) soma += Number(d[i]) * (n + 1 - i);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  return dv(9) === Number(d[9]) && dv(10) === Number(d[10]);
}

export function mascararCpf(cpf: string): string {
  const d = digitosCpf(cpf);
  return `***.***.**${d[8]}-${d.slice(9)}`;
}

/** Data no formato AAAA-MM-DD no fuso das unidades. */
export function diaLocal(data: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: FUSO, year: 'numeric', month: '2-digit', day: '2-digit' }).format(data);
}

/** Próximos dias úteis (segunda a sexta), a partir de hoje. */
export function proximosDiasUteis(quantidade: number, hoje = new Date()): string[] {
  const dias: string[] = [];
  const cursor = new Date(`${diaLocal(hoje)}T12:00:00-03:00`);
  while (dias.length < quantidade) {
    const semana = cursor.getUTCDay();
    if (semana !== 0 && semana !== 6) dias.push(diaLocal(cursor));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return dias;
}

export function horaLocal(iso: string, idioma: 'pt' | 'en' = 'pt'): string {
  return new Date(iso).toLocaleTimeString(idioma === 'pt' ? 'pt-BR' : 'en-US', { timeZone: FUSO, hour: '2-digit', minute: '2-digit' });
}

export function dataLonga(diaOuIso: string, idioma: 'pt' | 'en' = 'pt'): string {
  const data = diaOuIso.length === 10 ? new Date(`${diaOuIso}T12:00:00-03:00`) : new Date(diaOuIso);
  return data.toLocaleDateString(idioma === 'pt' ? 'pt-BR' : 'en-US', { timeZone: FUSO, weekday: 'short', day: '2-digit', month: 'short' });
}

package io.github.jvcamargo10.agendamento.dominio;

/** Violação de uma regra do negócio: vira um erro BAD_REQUEST na resposta GraphQL. */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}

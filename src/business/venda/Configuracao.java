package business.venda;

import business.produtos.ComparaPacotesByDesconto;
import business.produtos.Componente;
import business.produtos.Pacote;
import business.produtos.PacoteDormente;
import data.ComponenteDAO;
import data.PacoteDAO;
import javafx.util.Pair;

import java.sql.SQLException;
import java.util.*;

public class Configuracao {
	private Set<Componente> componentes; //Componentes da configuração
	private Set<Integer> dependentes; 	 //Id's dos componentes que precisam de ser adicionados
	private Set<Pacote> pacotes;		 //Pacotes da configuração
	private HashMap<Integer, PacoteDormente> pacotesDormentes;  //Pacotes que deixaram de estar ativos, mas podem voltar.
	private ComponenteDAO cDAO;
	private PacoteDAO pDAO;

	public Configuracao() {
		this.componentes = new HashSet<>();
		this.dependentes = new HashSet<>();
		this.pacotes = new HashSet<>();
		this.pacotesDormentes = new HashMap<>();
		this.cDAO = new ComponenteDAO();
		this.pDAO = new PacoteDAO();
	}

	/*
	 * Adiciona um componente, tratando as incompatibilidades e dependencias, assim como a formação de um pacote.
	 * @param idComponente id do componente a adicionar
	 * @returns valor a acrescentar à encomenda
	 */
	public Pair<Integer,Set<Integer>> adicionarComponente(int idComponente) throws SQLException {
		Componente componente = cDAO.get(idComponente);
		//Manhas para não fazer métodos diferentes. Todos recebem lista
		HashSet<Componente> componentesAdd = new HashSet<>();
		componentesAdd.add(componente);

		//Tratamentos
		//Valor retirado corresponde ao valor que os componentes a remover tinham e que vai ser subtraído
		//Valor Acrescentado é o valor dos componentes novos a adicionar. no tratarDependencias somente pq
		//faço lá o ciclo necessário para ter os valores.
		Pair <Integer,Set<Integer>> temp = tratarIncompatibilidades(componentesAdd);
		int valorAcrescentado = tratarDependencias(componentesAdd);

		//Adição do componente
		this.componentes.add(componente);

		//Se foi adicionado um dos dependentes !! Deprecated, pq se eu retirar um dos dependentes e depois o vendedor
		//remove-lo eu não sei que ele é dependente.
		//boolean escolha = dependentes.contains(idComponente);
		//if (escolha) dependentes.remove(idComponente);

		//Formação de pacote
		int descontoAcrescentado;
		//Damos prioridade aos dormentes. Podiam haver mais pacotes possíveis a ser formados, mas como não aceitamos conflitos
		//estes componentes estão reservados a pacotes inativos
		//Pacotes inativos podem ser de componentes removidos normalmente e não de dependentes, mas também parece-me indicado
		//dar prioridade a estes.
		if (pacotesDormentes.containsKey(idComponente)){
			descontoAcrescentado = ativaPacote(idComponente);
		}
		else {
			descontoAcrescentado = formacaoPacote(componente);
		}
		Set<Integer> pac = temp.getValue();
		int val = temp.getKey();



		return new Pair<Integer,Set<Integer>>((valorAcrescentado - val - descontoAcrescentado), pac);
	}
	/*
	 *Verifica se houve ativação de algum pacote
	 *@param idComponente id do componente que poderá ter formado pacote
	 *@returns val valor a descontar para o total da encomenda
	 */
	public int ativaPacote(int idComponente){
		PacoteDormente pd = pacotesDormentes.get(idComponente);
		boolean flag = pd.decr();
		if (flag) {
			return pd.getDesconto();
		}
		return 0;
	}

	/*
	 *Verifica se houve formou de algum pacote com a adição de um componente
	 *@param idComponente id do componente que poderá ter formado pacote
	 *@returns val valor a descontar para o total da encomenda
	 */
	public int formacaoPacote(Componente componente) throws SQLException {
		List<Pacote> pac;    //Pacotes que tem na sua constituição o componente fornecido como parámetro
		HashSet<Integer> aux;    //Componentes de um pacote
		TreeSet<Pacote> formados = new TreeSet<>(new ComparaPacotesByDesconto()); // pacotes que podem ser formados
		pac = pDAO.list(componente);
		boolean flag;            //Irá indicar se pode ser formado o pacote

		//!!!Talvez possa otimizar e trazer os pacotes para a memória, mas não sei se vale a pena
		//Por cada pacote que na sua constituição tem o componente vamos verificar se temos todos os componentes necessários
		for (Pacote p : pac) {
			aux = (HashSet<Integer>) p.getComponentes();
			flag = true;
			for (int id : aux) {
				for (Componente c : componentes)
					if (id != c.getId())
						flag = false;
			}
			if (flag) formados.add(p);
		}

		//É retirado o pacote com menor custo da estrutura auxiliar e adicionado à configuração
		Pacote p = formados.first();
		pacotes.add(p);

		return p.getDesconto();
	}
	/*
	 *Vai buscar as incompatibilidades dos componentes que recebeu como argumento e remove-as
	 *@param componentes Componentes cujas incompatibilidades serão removidas
	 */
	private Pair <Integer,Set<Integer>> tratarIncompatibilidades(Set<Componente> componentes) throws SQLException {
		HashSet<Integer> idIncompativeis = new HashSet<>();  //Id's dos componentes incompatíveis de todos os componentes
		Set<Integer> aux;									 //Id's dos componentes incompatíveis de cada um dos componentes

		//Vai buscar todas as incompatibilidades dos componentes
		for(Componente c : componentes){
			aux = c.getDependentesDasIncompatibilidades();
			idIncompativeis.addAll(aux);
		}
		return removerComponentes(componentes, idIncompativeis);
	}
	/*
	 *Vai buscar as dependências dos componentes a remover e remóveas da lista dependentes.
	 *Remove os componentes recebidos. Desfaz os pacotes onde estes componentes estavam,
	 !!! mas coloca-os como dormentes !!! Ver se vale a pena !!!
	 *@param idComponentes Componentes a ser removidos
	 *@returns valor a ser diminuido ao valor da encomenda
	 */
	private Pair<Integer,Set<Integer>> removerComponentes (Set<Componente> componentes, Set<Integer> idDependentesInc){
		int valorRetirado = 0; //valor a retirar da encomenda
		HashSet<Integer> pac = new HashSet<>();
		boolean found = false; //para não tirar o desconto várias vezes

		//Retira os dependentes da config.
		dependentes.removeAll(idDependentesInc);

		//Retira os componentes da config. e também o seu valor
		for(Componente c : componentes){
			if(componentes.contains(c)){
				valorRetirado+=c.getPreco();
				componentes.remove(c);}
		}
		for(Pacote p : pacotes) {
			found = false;
			for (int id : p.getComponentes()) {
				for (Componente c : componentes) {
					if (componentes.contains(id)) {
						//Se ficar assim pode ser otimizado
						if(!found){
							pacotes.remove(p);
							pac.add(p.getId());
							valorRetirado -= p.getDesconto();
							// Ver melhor isto !!!!
							for(int key : pacotesDormentes.keySet()){
								if(p.getComponentes().contains(key))
									pacotesDormentes.remove(key);
							}
							//PacoteDormente pd = new PacoteDormente(p,0);
						}
						//pd.incr();
						//pacotesDormentes.put(id,p);
						//found = true;
					}
				}
			}
		}

		return new Pair<Integer, Set<Integer>>(valorRetirado, pac);
	}
	/*
	 *Vais buscar dependencias e adiciona-as a this.dependentes
	 *@param componentes Componentes cujas dependências terão de ser adicionadas
	 *@returns val Valor dos componentes que vão ser adicionados a this.componentes ---- para aproveitar o ciclo
	 */
	private int tratarDependencias(Set<Componente> componentes){
		HashSet<Integer> idDependentes = new HashSet<>(); //Retem os id's dos componentes dependentes do Set fornecido
		HashSet aux = new HashSet();					  //Id's dos componentes dependentes de cada um dos componentes do Set fornecido
		int val = 0;

		//Vai buscar todas as dependências
		for(Componente c : componentes){
			int id = c.getId();
			val += c.getPreco();
			aux = (HashSet) c.getDepedendencias();
			idDependentes.addAll(aux);
		}
		//Se houver adiciona à lista de dependências da configuração
		if (idDependentes.size() != 0) {
			this.dependentes.addAll(idDependentes);
		}
		return val;
	}
	/*
	 *Remove um componente e as dependencias que gerou
	 *@param idComponente Id componente a ser retirado
	 */
	public Pair <Integer,Set<Integer>> removerComponente(int idComponente) throws ComponenteNaoExisteNaConfiguracao, SQLException {
		//Isto parece-me desnecessário
		Componente c = cDAO.get(idComponente);
		if (!componentes.contains(c)) throw new ComponenteNaoExisteNaConfiguracao("Componentes não existe");

		HashSet<Componente> componentes = new HashSet<>();
		Set<Integer> aux = c.getDependentesDasIncompatibilidades();
		componentes.add(c);

		return removerComponentes(componentes, aux);
	}

	/*
	 *Adiciona um pacote e os seus componentes. Trata dependencias, conflitos de pacotes e incompatibilidades.
	 *@param idPacote Id do pacote a ser adicionado
	 *@returns par em que a chave é a quantia a ser descontada na encomenda e o valor é a lista de pacotes que podem ter sido removidos
	 */
	public Pair<Integer,Set<Integer>> adicionarPacote(int idPacote) throws SQLException {
		Pacote p = pDAO.get(idPacote);
		HashSet<Componente> componentes = (HashSet<Componente>) p.getComponentesRef();

		//tratamentos
		Pair <Integer, Set<Integer>> temp = tratarIncompatibilidades(componentes);
		int valorAcrescentado = tratarDependenciasPacote(componentes,p);

		Set<Integer> pac = temp.getValue();
		int val = temp.getKey();

		return new Pair<Integer, Set<Integer>>((valorAcrescentado - val), pac);
	}
	private int tratarDependenciasPacote(Set<Componente> componentes, Pacote p){
		HashSet<Integer> idDependentes = new HashSet<>(); //Retem os id's dos componentes dependentes do Set fornecido
		HashSet aux = new HashSet();					  //Id's dos componentes dependentes de cada um dos componentes do Set fornecido
		int val = 0;

		//Vai buscar todas as dependências
		for(Componente c : componentes){
			int id = c.getId();
			val += c.getPreco();
			aux = (HashSet) c.getDepedendencias();
			idDependentes.addAll(aux);
		}
		//Se houver adiciona à lista de dependências da configuração
		if (idDependentes.size() == 0) {
			return val-=p.getDesconto();
		}
		this.dependentes.addAll(idDependentes);

		PacoteDormente pd = new PacoteDormente(p,0);
		for(int id : idDependentes) {
			pd.incr();
			pacotesDormentes.put(id, pd);
		}
		return val;
	}
	private float tratarIncompatibilidades(Set<Componente> componentes, Pacote p){return 1;}

	private boolean existeConflito(Set<Componente> componentes) {
		boolean found = false;
		Iterator<Pacote> it = pacotes.iterator();
		Set<Integer> compIds;

		while (it.hasNext() && !found) {
			compIds = it.next().getComponentes();
			for (Componente c : componentes) {
				int idC = c.getId();
				found = compIds.contains(idC);
			}
		}
		return found;
	}
	public Pair<Integer,Set<Integer>> removerPacote(int idPacote) throws PacoteNaoExisteNaConfiguracaoException, SQLException {
		Pacote p = pDAO.get(idPacote);
		if(!pacotes.contains(p)) throw new PacoteNaoExisteNaConfiguracaoException("Pacote não existe");

		Set<Componente> componentes = p.getComponentesRef();
		Set<Integer> aux = new HashSet<>();

			for(Componente c : componentes) {
				aux.addAll(c.getDependentesDasIncompatibilidades());
			}

		return removerComponentes(componentes, aux);

	}
	//meter a dar throw de exceção que não existem incompatibilidades
	public Pair<Set<Integer>,Set<Integer>> getEfeitosSecundariosAdicionarComponente (int idComponente) throws ComponenteJaExisteNaConfiguracaoException, SQLException {
		Componente c = cDAO.get(idComponente);
		if(!componentes.contains(c)) throw new ComponenteJaExisteNaConfiguracaoException("Já existe");

		Set<Integer> idIncompativeis = c.getIncompatibilidades();
		Set<Integer> idDependentes = c.getDepedendencias();

		Set<Integer> remocoes = getRemocoes(idIncompativeis);
		return new Pair<>(remocoes, idDependentes);
	}

	public Pair<Set<Integer>,Set<Integer>> getEfeitosSecundariosAdicionarPacote(int idPacote) throws PacoteJaExisteNaConfiguracaoException, PacoteGeraConflitosException, SQLException {
		Pacote pacote = pDAO.get(idPacote);

		if(!pacotes.contains(pacote)) throw new PacoteJaExisteNaConfiguracaoException("Já existe");

		if(existeConflito(componentes)) {
			throw new PacoteGeraConflitosException("Já existe um pacote com algum dos componentes do que pretende adicionar");
		}

		HashSet<Integer> idIncompativeis = new HashSet<>();
		HashSet<Integer> idDependentes = new HashSet<>();
		Set<Componente> componentes = pacote.getComponentesRef();


		for(Componente c : componentes){
			idIncompativeis.addAll(c.getIncompatibilidades());
			idDependentes.addAll(c.getDepedendencias());
		}

		Set<Integer> remocoes = getRemocoes(idIncompativeis);
		return new Pair<>(remocoes, idDependentes);
	}

	private Set<Integer> getRemocoes(Set<Integer> idIncompativeis){
		HashSet<Integer> incompARemover = new HashSet<>();
		//HashSet<Integer> pacotesARemover = new HashSet<>();
		for(int id : idIncompativeis){
			for(Componente c : componentes){
				int idC = c.getId();
				if(idC == id) incompARemover.add(id);
			}
			//for(Pacote p : pacotes){
			//	if(p.getComponentes().contains(id)) pacotesARemover.add(p.getId());
			//}
		}
		return incompARemover;
		//return new Pair<>(incompARemover, pacotesARemover);
	}
	protected void otimizarPacotes() throws SQLException {
		Set<Pacote> todosPacotes = new HashSet<>();
		for (Componente c : componentes){
			todosPacotes.addAll(pDAO.list(c));
		}
		Set<Pacote> otimos = calculaOtimos(todosPacotes);
		boolean reotimizacao = comparaPacotes(otimos);
		if(reotimizacao) {
			pacotes.clear();
			pacotes.addAll(otimos);
		}
	}
	//Por fazer
	private Set<Pacote> calculaOtimos(Set<Pacote> pacotes){throw new UnsupportedOperationException();}

	private boolean comparaPacotes(Set<Pacote> otimos){
		int valAtual = 0;
		int valOtimo = 0;

		//Por ser heurística
		for(Pacote p : otimos) valOtimo+=p.getDesconto();
		for(Pacote p : pacotes) valAtual+=p.getDesconto();

		if(valOtimo > valAtual) return true;

		return false;
	}

	public Set<Componente> atualizaStock() throws SQLException, FaltamDependentesException {
		boolean flag = false;
		for(Componente c : componentes){
			int id = c.getId();
			if (!dependentes.contains(id))
				throw new FaltamDependentesException("Insira os restantes dependentes");
		}

		return cDAO.atualizaStock(componentes);
	}

	public void configuracaoOtima() {
		throw new UnsupportedOperationException();
	}

	public Set<Componente> getComponentes() {
		return new HashSet<>(componentes);
	}

	public Set<Integer> getDependentes() {
		return new HashSet<>(dependentes);
	}

	public Set<Pacote> getPacotes() {
		return new HashSet<>(pacotes);
	}

	public void setcDAO(ComponenteDAO cDAO) {
		this.cDAO = cDAO;
	}

	public void setpDAO(PacoteDAO pDAO) {
		this.pDAO = pDAO;
	}
}
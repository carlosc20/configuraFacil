package business.venda.categorias;

import business.produtos.Componente;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public abstract class Categoria {

	public abstract String getDesignacao();

	abstract public boolean getObrigatoria();
}
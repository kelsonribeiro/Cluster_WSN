package projects.wsnee.nodes.nodeImplementations;

import java.awt.Color;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Set;

import projects.wsnee.nodes.messages.WsnMsg;
import projects.wsnee.nodes.messages.WsnMsgResponse;
import projects.wsnee.nodes.timers.WsnMessageTimer;
import projects.wsnee.utils.ArrayList2d;
import projects.wsnee.utils.Utils;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.runtime.Global;

public class SinkNode extends SimpleNode 
{

	/**
	 * Número de dados sensoriados por time slot (Tamanho do time slot)
	 * Number of sensed data per time slot (time slot size)
	 */
	private Integer sizeTimeSlot = 100;
	
	/**
	 * Tipo de dado a ser sensoriado (lido nos nós sensores), que pode ser: "t"=temperatura, "h"=humidade, "l"=luminosidade ou "v"=voltagem
	 * Type of data to be sensed (read in the sensor nodes), which can be: "t" = temperature, "h" = humidity, "l" = brightness or "v" = voltage
	 */
	private String dataSensedType = "t";
	
	/**
	 * Percentual do limiar de erro temporal aceitável para as leituras dos nós sensores, que pode estar entre 0.0 (não aceita erros) e 1.0 (aceita todo e qualquer erro)
	 * Percentage of temporal acceptable error threshold for the readings of sensor nodes, which may be between 0.0 (accepts no errors) and 1.0 (accepts any error)
	 */
	private double thresholdError = 0.1;
	
	/**
	 * Limite de diferença de magnitude aceitável (erro espacial) para as leituras dos nós sensores /--que pode estar entre 0.0 (não aceita erros) e 1.0 (aceita todo e qualquer erro)
	 * Limit of acceptable magnitude difference (spatial error) for the readings of sensor nodes / - which can be between 0.0 (no errors accepted) and 1.0 (accepts any error)
	 */
	private double spacialThresholdError = 1.5;
	
	/**
	 * Percentual mínimo do número de rounds iguais das medições de 2 sensores para que os mesmos sejam classificados no mesmo cluster
	 * Minimum percentage of the number of equal measurement rounds of 2 sensors so that they are classified in the same cluster
	 */
	private double equalRoundsThreshold = 0.5;
	
	/**
	 * Percentual mínimo das medições de 2 sensores (no mesmo round) a ficar dentro dos limiares aceitáveis para que os mesmos sejam classificados no mesmo cluster
	 * Minimum percentage of measurements of two sensors (in the same round) to stay within the acceptable thresholds for them to be classified in the same cluster
	 */
	private double metaThreshold = 0.5;
	
	/**
	 * Distância máxima aceitável para a formação de clusters. Se for igual a zero (0,0), não considerar tal limite (distância)
	 * Maximum distance acceptable to the formation of clusters. If it is equal to zero (0.0), ignoring
	 */
	private double maxDistance = 8.0;
	
	/**
	 * Número total de nós sensores presentes na rede
	 * Total number of sensor nodes in the network
	 */
	private static int numTotalOfSensors = 54;
	
	/**
	 * Array 2D (clusters) from sensors (Messages from sensors = WsnMsgResponse).
	 */
	private ArrayList2d<WsnMsgResponse> messageGroups;
	
	/**
	 * Number of messages received by sink node from all other sensors nodes
	 */
	private int numMessagesReceived = 0;
	
	/**
	 * Number of messages of error prediction received by sink node from all other sensors nodes
	 */
	private int numMessagesOfErrorPredictionReceived = 0;
	
	/**
	 * Number of messages of time slot finished received by sink node from all other sensors nodes
	 */
	private int numMessagesOfTimeSlotFinishedReceived = 0;
	
	public SinkNode()
	{
		super();
		this.setColor(Color.RED);
		Utils.printForDebug("The size of time slot is "+sizeTimeSlot);
		Utils.printForDebug("The type of data sensed is "+dataSensedType);
		Utils.printForDebug("The threshold of error (max error) is "+thresholdError);
		Utils.printForDebug("The size of sliding window is "+SimpleNode.slidingWindowSize);
		
//		if(LogL.ROUND_DETAIL){
			Global.log.logln("\nThe size of time slot is "+sizeTimeSlot);
			Global.log.logln("The type of data sensed is "+dataSensedType);
			Global.log.logln("The threshold of error (max error) is "+thresholdError);
			Global.log.logln("The size of sliding window is "+SimpleNode.slidingWindowSize+"\n");
//		}
	}

	@Override
	public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
		String text = "S";
		super.drawNodeAsSquareWithText(g, pt, highlight, text, 1, Color.WHITE);
	}
	
	@NodePopupMethod(menuText="Definir Sink como Raiz de Roteamento")
	public void construirRoteamento(){
		this.proximoNoAteEstacaoBase = this;
		//WsnMsg wsnMessage = new WsnMsg(1, this, null, this, 0); //Integer seqID, Node origem, Node destino, Node forwardingHop, Integer tipo
		WsnMsg wsnMessage = new WsnMsg(1, this, null, this, 0, sizeTimeSlot, dataSensedType); 
		WsnMessageTimer timer = new WsnMessageTimer(wsnMessage);
		timer.startRelative(1, this);
	}
	
	@Override
	public void handleMessages(Inbox inbox) 
	{
		while (inbox.hasNext())
		{
			Message message = inbox.next();
			if (message instanceof WsnMsgResponse)
			{
				this.setColor(Color.YELLOW);
				WsnMsgResponse wsnMsgResp = (WsnMsgResponse) message;
				
				Utils.printForDebug("@ @ @ Message Received by SINK from the NodeID = "+wsnMsgResp.origem.ID +" with MsgType = "+wsnMsgResp.tipoMsg+"\n");
				
				if (wsnMsgResp.tipoMsg == 2) // Se é uma mensagem de um Nó Representativo que excedeu o #máximo de erros de predição
				{
					numMessagesOfErrorPredictionReceived++;
					
// CASO O CLUSTER PRECISE SOFRER UM SPLIT, UMA MENS. SOLICITANDO UM NOVO ENVIO DE DADOS PARA O SINK DEVE SER ENVIADA PARA CADA UM DOS NÓS DO CLUSTER 
					
					receiveMessage(wsnMsgResp); // Recebe a mensagem, para recálculo dos coeficientes e reenvio dos mesmos àquele nó sensor (Nó Representativo), mantendo o número de predições a serem executadas como complemento do total calculado inicialmente, ou seja, NÃO reinicia o ciclo de time slot daquele cluster
				}
				else if (wsnMsgResp.tipoMsg == 3) // Se é uma mensagem de um Nó Representativo que excedeu o #máximo de predições (timeSlot)
				{
					numMessagesOfTimeSlotFinishedReceived++;
					int lineFromClusterNode = searchAndReplaceNodeInClusterByMessage(wsnMsgResp);
					if (lineFromClusterNode >= 0)
					{
						Utils.printForDebug("@ @ @ MessageGroups BEFORE classification:\n");
						printMessageGroupsArray2d();
						
						classifyRepresentativeNodesByResidualEnergy();
						
						Utils.printForDebug("@ @ @ MessageGroups AFTER FIRST classification:\n");
						printMessageGroupsArray2d();
						
						classifyRepresentativeNodesByHopsToSink();
						
						Utils.printForDebug("@ @ @ MessageGroups AFTER SECOND classification:\n");
						printMessageGroupsArray2d();
						
						WsnMsgResponse wsnMsgResponseRepresentative = messageGroups.get(lineFromClusterNode, 0); // Get the Representative Node (or Cluster Head)
						int numSensors = messageGroups.getNumCols(lineFromClusterNode);
						Utils.printForDebug("Cluster / Line number = "+lineFromClusterNode+"\n");
						wsnMsgResponseRepresentative.calculatesTheSizeTimeSlotFromRepresentativeNode(sizeTimeSlot, numSensors);
						receiveMessage(wsnMsgResponseRepresentative);
					}
					// PAREI AQUI!!! - Fazer testes para verificar se os clusters estão sendo reconfigurados quando um No Repres. finaliza seu time slot e atualiza o status de sua bateria!
				}
				else
				{
					addNodeInClusterClassifiedByMessage(wsnMsgResp);
					
					numMessagesReceived++;
					
					if (numMessagesReceived >= numTotalOfSensors)
					{
						Utils.printForDebug("@ @ @ MessageGroups BEFORE classification:\n");
						printMessageGroupsArray2d();
						
						classifyRepresentativeNodesByResidualEnergy();
						
						Utils.printForDebug("@ @ @ MessageGroups AFTER FIRST classification:\n");
						printMessageGroupsArray2d();
						
						classifyRepresentativeNodesByHopsToSink();
						
						Utils.printForDebug("@ @ @ MessageGroups AFTER SECOND classification:\n");
						printMessageGroupsArray2d();
						
						if (messageGroups != null) // If there is a message group created
						{
							for (int line=0; line < messageGroups.getNumRows(); line++) // For each line (group/cluster) from messageGroups
							{
								WsnMsgResponse wsnMsgResponseRepresentative = messageGroups.get(line, 0); // Get the Representative Node (or Cluster Head)
								int numSensors = messageGroups.getNumCols(line);
								Utils.printForDebug("Cluster / Line number = "+line);
								wsnMsgResponseRepresentative.calculatesTheSizeTimeSlotFromRepresentativeNode(sizeTimeSlot, numSensors);
								receiveMessage(wsnMsgResponseRepresentative);
							}
						}
					}
				}
			} //if (message instanceof WsnMsg)
		} //while (inbox.hasNext())
	} //public void handleMessages
	
	
	/**
	 * It selects the Representative Node for each line (group) from sensors by the max residual energy and puts him in the first position (in line)
	 */
	private void classifyRepresentativeNodesByResidualEnergy()
	{
		if (messageGroups != null) // If there is a message group created
		{
			for (int line=0; line < messageGroups.getNumRows(); line++)
			{
				double maxBatLevel = 0.0;
				int maxBatLevelIndexInThisLine = 0;
				int bubbleLevel = 0;
				while (bubbleLevel < (messageGroups.getNumCols(line) - 1))
				{
					for (int col=bubbleLevel; col < messageGroups.getNumCols(line); col++)
					{
						WsnMsgResponse currentWsnMsgResp = messageGroups.get(line, col);
						double currentBatLevel = currentWsnMsgResp.batLevel;
						int currentIndex = col;
						if (currentBatLevel > maxBatLevel)
						{
							maxBatLevel = currentBatLevel;
							maxBatLevelIndexInThisLine = currentIndex;
						}
					}
					if (maxBatLevelIndexInThisLine != 0)
					{
						messageGroups.move(line, maxBatLevelIndexInThisLine, bubbleLevel);//changeMessagePositionInLine(line, maxBatLevelIndexInThisLine);
					}
					bubbleLevel++;
					maxBatLevel = 0.0;
					maxBatLevelIndexInThisLine = 0;
				}
			}
		}
	}
	
	private void classifyRepresentativeNodesByHopsToSink()
	{
		if (messageGroups != null) // If there is a message group created
		{
			for (int line=0; line < messageGroups.getNumRows(); line++)
			{
				if (messageGroups.get(line, 0) != null)
				{
					int minIndexNumHopsToSink = 0;
					boolean sameBatLevel = false;
					WsnMsgResponse firstWsnMsgRespInLine = messageGroups.get(line, 0);
					int col=1;
					while ((col < messageGroups.getNumCols(line)) && (messageGroups.get(line, col).batLevel == firstWsnMsgRespInLine.batLevel) && (messageGroups.get(line, col).saltosAteDestino < firstWsnMsgRespInLine.saltosAteDestino) )
					{
						minIndexNumHopsToSink = col;
						sameBatLevel = true;
						col++;
					}
					if (sameBatLevel)
					{
						changeMessagePositionInLine(line, minIndexNumHopsToSink);
					}
				}
			}
		}
	}
	
	private void printMessageGroupsArray2d()
	{
		if (messageGroups != null) // If there is a message group created
		{
			for (int line=0; line < messageGroups.getNumRows(); line++)
			{
				for (int col=0; col < messageGroups.getNumCols(line); col++)
				{
					WsnMsgResponse currentWsnMsgResp = messageGroups.get(line, col);
					Utils.printForDebug("Line = "+line+", Col = "+col+": NodeID = "+currentWsnMsgResp.origem.ID+" BatLevel = "+currentWsnMsgResp.batLevel+" Round = "+((SimpleNode)currentWsnMsgResp.origem).ultimoRoundLido);
				}
				Utils.printForDebug("\n");
			}
			Utils.printForDebug("Number of Lines / Clusters = "+messageGroups.getNumRows()+"\n");
		}
	}
	
	/**
	 * Change the message from "index" position for the first position [0] in that line from array
	 * @param line
	 * @param index
	 */
	private void changeMessagePositionInLine(int line, int index)
	{
		messageGroups.move(line, index, 0);
	}
	
	/**
	 * Adds the WsnMsgResponse object, passed by parameter, in the correct line ("Cluster") from the messageGroups (ArrayList2d) according with the Dissimilarity Measure 
	 * PS.: Each line in "messageGroups" (ArrayList2d of objects WsnMsgResponse) represents a cluster of sensors (WsnMsgResponse.origem), 
	 * classified by Dissimilarity Measure from yours data sensed, stored on WsnMsgResponse.dataRecordItens
	 *  
	 * @param wsnMsgResp Message to be used for classify the sensor node
	 */
	private void addNodeInClusterClassifiedByMessage(WsnMsgResponse newWsnMsgResp)
	{
		if (messageGroups == null) // If there isn't a message group yet, then it does create one and adds the message to it
		{
			messageGroups = new ArrayList2d<WsnMsgResponse>();
			messageGroups.ensureCapacity(numTotalOfSensors); // Ensure the capacity as the total number of sensors (nodes) in the data set
			messageGroups.add(newWsnMsgResp, 0); // Add the initial message to the group (ArrayList2d of WsnMsgResponse)
		}
		else
		{
			boolean found = false;
			int line = 0;
			while ((!found) && (line < messageGroups.getNumRows()))
			{
				int col = 0;
				boolean continueThisLine = true;
				while ((continueThisLine) && (col < messageGroups.getNumCols(line)))
				{
					WsnMsgResponse currentWsnMsgResp = messageGroups.get(line, col);
					if (testDistanceBetweenSensorPositions(currentWsnMsgResp, newWsnMsgResp))
					{
// DANDO ERRO NO TESTE DE DIST.
						if (testSimilarityMeasureWithPairRounds(currentWsnMsgResp, newWsnMsgResp)) // If this (new)message (with sensor readings) already is dissimilar to current message
						{
							continueThisLine = true; // Then this (new)message doesn't belong to this cluster / line / group
						}
						else
						{
							continueThisLine = false;
						}
					}
					col++;
				}
				if ((continueThisLine) && (col == messageGroups.getNumCols(line)))
				{
					found = true;
					messageGroups.add(newWsnMsgResp, line);
				}
				else
				{
					line++;
				}
			}
			if (!found)
			{
				messageGroups.add(newWsnMsgResp, messageGroups.getNumRows()); // It adds the new message "wsnMsgResp" in a new line (cluster) of messageGroup 
			}
/*				
			for (int line = 0; line < messageGroups.getNumRows(); line++)
			{
				for (int col = 0; col < messageGroups.getNumCols(line); col++)
				{
					
				}
			}
*/			
		}
	}

	/**
	 * Search the WsnMsgResponse object with the same source node, in the correct position in "Cluster" from the messageGroups (ArrayList2d) and replace him with the one passed by parameter  
	 * PS.: Each line in "messageGroups" (ArrayList2d of objects WsnMsgResponse) represents a cluster of sensors (WsnMsgResponse.origem), 
	 * classified by Dissimilarity Measure from yours data sensed, stored on WsnMsgResponse.dataRecordItens
	 *  
	 * @param wsnMsgResp Message to be used for classify the sensor node
	 */
	private int searchAndReplaceNodeInClusterByMessage(WsnMsgResponse newWsnMsgResp)
	{
		int lineCLuster = -1;
		if (messageGroups == null) // If there isn't a message group yet, then it does create one and adds the message to it
		{
			Utils.printForDebug("ERROR in searchAndReplaceNodeInClusterByMessage method: There isn't messageGroups object instanciated yet!");
		}
		else
		{
			boolean found = false;
			int line = 0, col = 0;
			while ((!found) && (line < messageGroups.getNumRows()))
			{
				col = 0;
				while ((!found) && (col < messageGroups.getNumCols(line)))
				{
					WsnMsgResponse currentWsnMsgResp = messageGroups.get(line, col);
					if (isEqualNodeSourceFromMessages(currentWsnMsgResp, newWsnMsgResp))
					{
						found = true;
					}
					else
					{
						col++;
					}
				}
				if (!found)
				{
					line++;
				}
			}
			if (found)
			{
				lineCLuster = line;
				messageGroups.set(line, col, newWsnMsgResp); // It sets the new message "wsnMsgResp" in the line and col (cluster) of messageGroup 
			}
		}
		return lineCLuster;
	}

	
	/**
	 * Testa se a distância entre os nós sensores que enviaram tais mensagens (currentWsnMsg e newWsnMsg) é menor ou igual a máxima distância possível (maxDistance) para os dois nós estarem no mesmo cluster
	 * @param currentWsnMsg Mensagem atual já classificada no cluster
	 * @param newWsnMsg Nova mensagem a ser classificada no cluster
	 * @return Retorna "verdadeiro" caso a distância entre os nós sensores que enviaram as mensagens não ultrapassa o limite máximo, "falso" caso contrário
	 */
	private boolean testDistanceBetweenSensorPositions(WsnMsgResponse currentWsnMsg, WsnMsgResponse newWsnMsg)
	{
		boolean distanceOK = false;
		if (maxDistance > 0.0)
		{
			if (currentWsnMsg.spatialPos != null && newWsnMsg.spatialPos != null && currentWsnMsg.spatialPos.distanceTo(newWsnMsg.spatialPos) <= maxDistance)
			{
				distanceOK = true;
			}
/*
			else
			{
				distanceOK = false;
			}
*/
			// distanceOK = (currentWsnMsg.spatialPos.distanceTo(newWsnMsg.spatialPos) <= maxDistance); //Another form for the "if" (above) 
		}
		else if (maxDistance == 0.0) // Case the distance between sensors should be ignored
		{
			distanceOK = true;
		}
		return distanceOK;
	}
	
	private boolean isEqualNodeSourceFromMessages(WsnMsgResponse currentWsnMsg, WsnMsgResponse newWsnMsg)
	{
		return (currentWsnMsg.origem.ID == newWsnMsg.origem.ID);
	}
	
/*
	private boolean testDissimilarityMeasureWithoutPairRounds(WsnMsgResponse currentWsnMsg, WsnMsgResponse newWsnMsg)
	{
		return true;
	}
*/
	
	/**
	 * It tests if there is dissimilarity (lack of similarity) between the 2 set of measure from 2 sensor brought by the 2 messages
	 * @param currentWsnMsg Represents the current message from the group of messages (messageGroups) in a "ArrayList2d<WsnMsgResponse>" structure
	 * @param newWsnMsg Represents the recently arrived message in the sink node, sent from the source sensor node
	 * @return True case the two messages are DISsimilar, i.e., from different clusters (or "groups"); False, otherwise
	 */
	
	private boolean testSimilarityMeasureWithPairRounds(WsnMsgResponse currentWsnMsg, WsnMsgResponse newWsnMsg)
	{
//		boolean sameSize = true;
		boolean mSimilarityMagnitude = false;
		boolean tSimilarityTrend = false;
		
		int currentSize = currentWsnMsg.dataRecordItens.size();
		double[] currentValues = new double[currentSize];
/*
		double[] currentTimes = new double[currentSize];
		char[] currentTypes = new char[currentSize];
		double[] currentBatLevel = new double[currentSize];
*/
		int[] currentRound = new int[currentSize];
		
		//Data read from current sensor (from ArrayList2d)
		currentValues = currentWsnMsg.getDataRecordValues();
/*
		currentTimes = currentWsnMsg.getDataRecordTimes();
		currentTypes = currentWsnMsg.getDataRecordTypes();
		currentBatLevel = currentWsnMsg.getDataRecordBatLevels();
*/
		currentRound = currentWsnMsg.getDataRecordRounds();

		
		int newSize = newWsnMsg.dataRecordItens.size();
		double[] newValues = new double[newSize];
/*
		double[] newTimes = new double[newSize];
		char[] newTypes = new char[newSize];
		double[] newBatLevel = new double[newSize];
*/
		int[] newRound = new int[newSize];
		
		//Data read from new sensor (from message received)
		newValues = newWsnMsg.getDataRecordValues();
/*
		newTimes = newWsnMsg.getDataRecordTimes();
		newTypes = newWsnMsg.getDataRecordTypes();
		newBatLevel = newWsnMsg.getDataRecordBatLevels();
*/
		newRound = newWsnMsg.getDataRecordRounds();

		HashMap<Integer, Double> hashCurrentMsg, hashNewMsg;
		
		hashCurrentMsg = new HashMap<Integer, Double>();
		hashNewMsg = new HashMap<Integer, Double>();
		
		// Populates 2 HashMaps with the values from currentWsnMsg and newWsnMsg
		for (int i=0,j=0; (i < currentSize || j < newSize); i++, j++)
		{
			if (i < currentSize)
			{	
				hashCurrentMsg.put(currentRound[i], currentValues[i]);
			}
			if (j < newSize)
			{
				hashNewMsg.put(newRound[j], newValues[j]);
			}
		}


//		int maxSizeOf2Msg = Math.max(currentSize, newSize);		
		
//		int numDissimilarity = 0;
		
		int numEqualKeys = 0;
		
		double sumDifs = 0.0;
		
		Set<Integer> keys = hashCurrentMsg.keySet();
		for (Integer key : keys)
		{
			if (hashNewMsg.containsKey(key))
			{
				numEqualKeys++;
				double curValue = hashCurrentMsg.get(key);
				double newValue = hashNewMsg.get(key);
				sumDifs += Math.abs(curValue - newValue);
			}
		}

		if ((numEqualKeys > 0) && (sumDifs/numEqualKeys <= spacialThresholdError))
		{
			mSimilarityMagnitude = true;
//			return mSimilarityMagnitude;
		}
/*
		if ((numEqualKeys >= equalRoundsThreshold * maxSizeOf2Msg) && (numDissimilarity >= metaThreshold * numEqualKeys))
		{
			mSimilarityMagnitude = true;
			return mSimilarityMagnitude;
		}
*/		
		double contN1 = 0.0;
		double contN = currentSize; // = newSize; // Total size of sensed values from node
		for (int i=1,j=1; (i < currentSize && j < newSize); i++, j++)
		{
			double difX, difY;			
			difX = (currentValues[i] - currentValues[i-1]);
			difY = (newValues[j] - newValues[j-1]);
			if ((difX * difY) >= 0)
			{
				contN1++;
			}
		}
		if ((contN > 0.0) && (contN1/contN >= thresholdError))
		{
			tSimilarityTrend = true;
//			return tDissimilarityTrendFound;
		}
		
/*
		if (currentSize != newSize)
		{
			sameSize = false; // Size from (2) data sets are different
		}
		
		if (sameSize && compareDataSetValuesPairToPair(currentValues, newValues, currentSize))
		{
			
		}
*/	
		return (mSimilarityMagnitude && tSimilarityTrend);
	}

	private boolean compareDataSetValuesPairToPair(double[] valuesC, double[] valuesN, int size)
	{
		boolean ok = true;
		int cont = 0;
		while (ok && (cont<size))
		{
			if (Math.abs(valuesC[cont] - valuesN[cont]) > spacialThresholdError)
			{
				ok = false;
			}
			cont++;
		}
		return ok;
	}
	
	/**
	 * Recebe a mensagem passada, lê os parâmetros (itens) no dataRecordItens, calcula os coeficientes A e B de acordo com estes parâmetros e envia tais coeficientes para o nó sensor de origem
	 * [Eng] Receives the message, it reads the parameters (items) in dataRecordItens, calculates the coefficients A and B according to these parameters and sends these coefficients for the sensor node of origin
	 * @param wsnMsgResp Mensagem recebida com os parâmetros a serem lidos
	 */
	private void receiveMessage(WsnMsgResponse wsnMsgResp)
	{
		if (wsnMsgResp != null && wsnMsgResp.dataRecordItens != null)
		{
			int size = wsnMsgResp.dataRecordItens.size();
			double[] valores = new double[size];
			double[] tempos = new double[size];
//			char[] tipos = new char[size];
			//Dados lidos do sensor correspondente
			valores = wsnMsgResp.getDataRecordValues();
			tempos = wsnMsgResp.getDataRecordTimes();
//			tipos = wsnMsgResp.getDataRecordTypes();
			//Coeficientes de regressão linear com os vetores acima
			double coeficienteA, coeficienteB;
			double mediaTempos, mediaValores;
			//Médias dos valores de leitura e tempos
			mediaTempos = calculaMedia(tempos);
			mediaValores = calculaMedia(valores);
			//Cálculos dos coeficientes de regressão linear com os vetores acima
			coeficienteB = calculaB(valores, tempos, mediaValores, mediaTempos);
			coeficienteA = calculaA(mediaValores, mediaTempos, coeficienteB);
			sendCoefficients(wsnMsgResp, coeficienteA, coeficienteB);
		}
	}
	
	/**
	 * Calcula e retorna a média aritmética dos valores reais passados
	 * @param values Array de valores reais de entrada 
	 * @return Média dos valores reais de entrada
	 */
	private double calculaMedia(double[] values)
	{
		double mean = 0, sum = 0;
		for (int i=0; i<values.length; i++)
		{
			sum += values[i];
		}
		if (values.length > 0)
		{
			mean = sum/values.length;
		}
		return mean;
	}
			
	/**
	 * Calcula o coeficiente B da equação de regressão
	 * @param valores Array de valores (grandezas) das medições dos sensores
	 * @param tempos Array de tempos das medições dos sensores
	 * @param mediaValores Média dos valores
	 * @param mediaTempos Média dos tempos
	 * @return Valor do coeficiente B da equação de regressão
	 */
	private double calculaB(double[] valores, double[] tempos, double mediaValores, double mediaTempos)
	{
		double numerador = 0.0, denominador = 0.0, x;
		for (int i = 0; i < tempos.length; i++)
		{
			x = tempos[i] - mediaTempos;
			numerador += x*(valores[i] - mediaValores);
			denominador += x*x;
		}
		if (denominador != 0)
		{
			return (numerador/denominador);
		}
		return 0.0;
	}
	
	/**
	 * Calcula o coeficiente A da equação de regressão
	 * @param mediaValores Média dos valores lidos pelos sensores
	 * @param mediaTempos Média dos tempos de leitura dos valores pelos sensores
	 * @param B Valor do coeficiente B da equação de regressão
	 * @return Valor do coeficiente A
	 */
	private double calculaA(double mediaValores, double mediaTempos, double B)
	{
		return (mediaValores - B*mediaTempos);
	}
	
	/**
	 * Cria uma nova mensagem (WsnMsg) para envio dos coeficientes recebidos através dos parâmetros, e a envia para o próximo nó no caminho até o nó de origem da mensagem (wsnMsgResp.origem)
	 * @param wsnMsgResp Mensagem de resposta enviada do nó de origem para o nó sink, que agora enviará os (novos) coeficientes calculados para o nó de origem 
	 * @param coeficienteA Valor do coeficiente A da equação de regressão
	 * @param coeficienteB Valor do coeficiente B da equação de regressão
	 */
	private void sendCoefficients(WsnMsgResponse wsnMsgResp, double coeficienteA, double coeficienteB)
	{
		
		WsnMsg wsnMessage = new WsnMsg(1, this, wsnMsgResp.origem , this, 1, wsnMsgResp.sizeTimeSlot, dataSensedType, thresholdError);
		wsnMessage.setCoefs(coeficienteA, coeficienteB);
		wsnMessage.setPathToSenderNode(wsnMsgResp.clonePath());
		sendToNextNodeInPath(wsnMessage);
	}
}
